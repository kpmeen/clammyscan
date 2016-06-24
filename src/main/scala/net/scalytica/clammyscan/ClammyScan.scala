package net.scalytica.clammyscan

import java.net.URLDecoder.decode

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import net.scalytica.clammyscan.ClamConfig._
import play.api.Logger
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.BodyParsers.parse._
import play.api.mvc._
import play.core.parsers.Multipart.FileInfo

import scala.concurrent.{ExecutionContext, Future}

/**
 * Enables streaming upload of files/attachments with custom metadata to GridFS
 */
trait ClammyScan {

  implicit val system: ActorSystem
  implicit val materializer: Materializer

  val cbpLogger = Logger(classOf[ClammyScan])

  /**
   *
   * @param filename
   * @param ec
   * @return
   */
  private[this] def clammySink(
    filename: String
  )(implicit ec: ExecutionContext) = {
    if (!scanDisabled) {
      ClamIO().scan(filename)
    } else {
      // Scanning disabled
      cbpLogger.warn(s"Scanning is disabled. $filename will not be scanned")
      ClamIO.cancelled(Right(FileOk()))
    }
  }

  /**
   *
   * @param c
   * @param s
   * @param e
   * @tparam A
   * @return
   */
  def broadcastGraph[A](
    c: ClamSink,
    s: SaveSink[A]
  )(implicit e: ExecutionContext): Sink[ByteString, Future[TupledResponse[A]]] =
    Sink.fromGraph[ByteString, (Future[ClamResponse], Future[Option[A]])] {
      GraphDSL.create(c, s)((cs, ss) => (cs, ss)) { implicit b => (cs, ss) =>
        import GraphDSL.Implicits._

        val bro = b.add(Broadcast[ByteString](2))
        bro ~> cs
        bro ~> ss

        SinkShape(bro.in)
      }
    }.mapMaterializedValue { mat =>
      for {
        cr <- mat._1.recover {
          case ClammyException(err) =>
            Left(err)
          case stex: StreamTcpException =>
            Left(ScanError(stex.getMessage))
        }
        sr <- mat._2
      } yield (cr, sr)
    }

  /**
   *
   * @param filename
   * @param contentType
   * @param save
   * @param ec
   * @tparam A
   * @return
   */
  def sinks[A](filename: String, contentType: Option[String])(
    save: (String, Option[String]) => SaveSink[A]
  )(implicit ec: ExecutionContext): (ClamSink, SaveSink[A]) =
    if (fileNameValid(filename)) {
      (clammySink(filename), save(filename, contentType))
    } else {
      val errSave = Sink.cancelled[ByteString].mapMaterializedValue { _ =>
        Future.successful(None)
      }
      val errScan = ClamIO.cancelled(Left(
        InvalidFilename(s"Filename $filename contains illegal characters")
      ))

      (errScan, errSave)
    }

  /**
   *
   * @param save
   * @param remove
   * @param ec
   * @tparam A
   * @return
   */
  def scan[A](
    save: (String, Option[String]) => SaveSink[A],
    remove: A => Unit
  )(implicit ec: ExecutionContext): ClamParser[A] = {
    cbpLogger.debug("Preparing to scan file")
    multipartFormData[TupledResponse[A]] {
      case FileInfo(partName, filename, contentType) =>

        cbpLogger.debug(s"Setting up sinks to scan $filename")
        val theSinks = sinks(filename, contentType)(save)
        val comb = broadcastGraph(theSinks._1, theSinks._2)

        cbpLogger.debug(s"Preparing accumulator...")
        Accumulator(comb).map { ref =>
          MultipartFormData.FilePart(partName, filename, contentType, ref)
        }

    }.validateM((data: MultipartFormData[TupledResponse[A]]) => {
      cbpLogger.debug(s"Data is:\n $data")

      data.files.headOption.map(hf => hf.ref._1 match {
        case Left(err) =>
          // Ooops...there seems to be a problem with the clamd scan result.
          val maybeFile = data.files.headOption.flatMap(_.ref._2)
          handleError(data, err) {
            maybeFile.foreach(f => remove(f))
          }
        case Right(ok) =>
          // It's all good...
          Future.successful(Right(data))
      }).getOrElse {
        Future.successful {
          Left(Results.BadRequest(Json.obj(
            "message" -> "Unable to locate any files after scan result"
          )))
        }
      }
    })
  }

  /**
   * Scans file for virus and buffers to a temporary file. Temp file is
   * removed if file is infected.
   */
  def scanWithTmpFile(implicit e: ExecutionContext): ClamParser[TemporaryFile] =
    scan[TemporaryFile](
      save = { (fname, ctype) =>
      val tempFile = TemporaryFile("multipartBody", "scanWithTempFile")
      tempFile.file.deleteOnExit()
      FileIO.toFile(tempFile.file).mapMaterializedValue { _ =>
        Future.successful(Option(tempFile))
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
      save = (fname, ctype) =>
      Sink.cancelled[ByteString]
        .mapMaterializedValue(_ => Future.successful(None)),
      remove = _ =>
      cbpLogger.debug("Only scanning, no file to remove")
    )

  /**
   * Function specifically for handling the ClamError cases in the
   * validation step
   */
  private def handleError[A](
    fud: MultipartFormData[TupledResponse[A]],
    err: ClamError
  )(onError: => Unit)(implicit ec: ExecutionContext) = {
    err match {
      case vf: VirusFound =>
        // We have encountered the dreaded VIRUS...run awaaaaay
        if (canRemoveInfectedFiles) {
          temporaryFile.map(_.file.delete())
        }
        Future.successful(Left(Results.NotAcceptable(
          Json.obj("message" -> vf.message)
        )))
      case err: ScanError =>
        if (canRemoveOnError) {
          onError
        }
        if (shouldFailOnError) {
          Future.successful(Left(Results.BadRequest(
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