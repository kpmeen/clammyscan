package net.scalytica.clammyscan

import java.net.URLDecoder.decode

import akka.actor.ActorSystem
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.util.ByteString
import net.scalytica.clammyscan.ClammyParserConfig._
import play.api.Logger
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.libs.streams.{Accumulator, Streams}
import play.api.mvc.BodyParsers.parse._
import play.api.mvc._
import play.core.parsers.Multipart.FileInfo

import scala.concurrent.{ExecutionContext, Future}

/**
 * Enables streaming upload of files/attachments with custom metadata to GridFS
 */
// TODO: Consider making a class with injected ActorSystem and ActorMaterializer
trait ClammyBodyParsers {
  self: Controller =>

  implicit val system = ActorSystem("test")
  implicit val materializer = ActorMaterializer()

  val cbpLogger = Logger(classOf[ClammyBodyParsers])

  private val couldNotConnect = ScanError("Could not connect to clamd")

  private[this] def clammySink(
    filename: String
  )(implicit ec: ExecutionContext) = {
    if (!scanDisabled) {
      val socket = ClamSocket()
      if (socket.isConnected) {
        val clamav = new ClammyScan(socket)
        val cav = clamav.clamScan(filename)
        // Temporary conversion of Iteratee => Sink
        Streams.iterateeToAccumulator(cav)
          .toSink
          .contramap[ByteString](_.toArray[Byte])
          .mapMaterializedValue(_.flatMap(f => f))
      } else {
        // Clam connection failed
        Sink.cancelled[ByteString].mapMaterializedValue { _ =>
          Future.successful(Left(couldNotConnect))
        }
      }
    } else {
      // Scanning disabled
      cbpLogger.warn(s"Scanning is disabled. $filename will not be scanned")
      Sink.cancelled[ByteString].mapMaterializedValue { _ =>
        Future.successful(Right(FileOk()))
      }
    }
  }

  private[this] def broadcastGraph[A](c: ClamSink, s: SaveSink[A]) =
    GraphDSL.create(c, s)((cs, ss) => (cs, ss)) { implicit b => (cs, ss) =>
      import GraphDSL.Implicits._
      val bro = b.add(Broadcast[ByteString](2))
      bro ~> cs
      bro ~> ss
      ClosedShape
    }

  private[this] def sinks[A](
    filename: String,
    contentType: Option[String]
  )(
    save: (String, Option[String]) => SaveSink[A]
  )(implicit ec: ExecutionContext): (ClamSink, SaveSink[A]) =
    if (fileNameValid(filename)) {
      (clammySink(filename), save(filename, contentType))
    } else {
      val errSave = Sink.cancelled[ByteString].mapMaterializedValue(_ => None)
      val errScan = Sink.cancelled[ByteString].mapMaterializedValue { _ =>
        Future.successful(Left(InvalidFilename(
          s"Filename $filename contains illegal characters"
        )))
      }

      (errScan, errSave)
    }

  def scan[A](
    save: (String, Option[String]) => SaveSink[A],
    remove: A => Unit
  )(implicit ec: ExecutionContext): ClamParser[A] = {
    parse.using { request =>
      multipartFormData[TupledResponse[A]] {
        case FileInfo(partName, filename, contentType) =>
          val theSinks = sinks(filename, contentType)(save)
          val graph = broadcastGraph(theSinks._1, theSinks._2)

          Accumulator.done(RunnableGraph.fromGraph(graph).run()).map { ref =>
            MultipartFormData.FilePart(partName, filename, contentType, ref)
          }
      }.validateM((futureData: MultipartFormData[TupledResponse[A]]) => {
        val data = futureData
        data.files.headOption.map(hf => hf.ref._1.flatMap {
          case Left(err) =>
            // Ooops...there seems to be a problem with the clamd scan result.
            val maybeFile = data.files.headOption.flatMap(_.ref._2)
            handleError(futureData, err) {
              maybeFile.foreach(f => remove(f))
            }
          case Right(ok) =>
            // It's all good...
            Future.successful(Right(futureData))
        }).getOrElse {
          Future.successful {
            Left(InternalServerError(Json.obj(
              "message" -> "Unable to locate any files after scan result"
            )))
          }
        }
      })
    }
  }

  /**
   * Scans file for virus and buffers to a temporary file. Temp file is
   * removed if file is infected.
   */
  def scanWithTempFile(
    implicit ec: ExecutionContext
  ): ClamParser[TemporaryFile] =
    scan[TemporaryFile](
      save = { (fname, ctype) =>
        val tempFile = TemporaryFile("multipartBody", "scanWithTempFile")
        tempFile.file.deleteOnExit()
        FileIO.toFile(tempFile.file).mapMaterializedValue { _ =>
          Option(tempFile)
        }
      },
      remove = tmpFile => tmpFile.file.delete()
    )

  /**
   * Mostly for convenience this. If you need a service for just scanning
   * a file for infections, this is it.
   */
  def scanOnly(implicit ec: ExecutionContext): ClamParser[Unit] =
    scan[Unit](
      save = (fname, ctype) => Sink.cancelled[ByteString]
        .mapMaterializedValue(_ => None),
      remove = _ => cbpLogger.debug("Only scanning, no file to remove")
    )

  /**
   * Function specifically for handling the ClamError cases in the
   * validation step
   */
  private def handleError[A](
    fud: MultipartFormData[(Future[ClamResponse], A)],
    err: ClamError
  )(onError: => Unit)(implicit ec: ExecutionContext) = {
    err match {
      case vf: VirusFound =>
        // We have encountered the dreaded VIRUS...run awaaaaay
        if (canRemoveInfectedFiles) {
          temporaryFile.map(_.file.delete())
        }
        Future.successful(Left(NotAcceptable(
          Json.obj("message" -> vf.message)
        )))
      case err: ScanError =>
        if (canRemoveOnError) {
          onError
        }
        if (shouldFailOnError) {
          Future.successful(Left(BadRequest(
            Json.obj("message" -> "File size exceeds maximum file size limit.")
          )))
        } else {
          Future.successful(Right(fud))
        }
    }
  }

  /**
   * Will validate the filename based on the configured regular expression
   * defined in application.conf.
   */
  private[this] def fileNameValid(filename: String): Boolean =
    validFilenameRegex.forall(regex =>
      regex.r.findFirstMatchIn(decode(filename, Codec.utf_8.charset)) match {
        case Some(m) => false
        case _ => true
      })

}