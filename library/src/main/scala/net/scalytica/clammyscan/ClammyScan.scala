package net.scalytica.clammyscan

import java.net.URLDecoder.decode

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import com.google.inject.Inject
import play.api.libs.Files.{TemporaryFile, TemporaryFileCreator}
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc._
import play.api.{Configuration, Logger}
import play.core.parsers.Multipart.FileInfo

import scala.concurrent.{ExecutionContext, Future}

/**
 * Enables parallel AV scanning of files/attachments being uploaded.
 */
trait ClammyScan { self =>

  protected val cbpLogger = Logger(self.getClass)

  val system: ActorSystem
  val materializer: Materializer
  val clamConfig: ClamConfig

  /**
   * Scans a file for virus and persists it with the `save` function.
   * By default, any infected files will be immediately removed using the
   * `remove` function.
   */
  def scan[A](
      save: ToSaveSink[A],
      remove: A => Unit
  )(implicit ec: ExecutionContext): ClamParser[A]

  /**
   * Scans file for virus and writes to a temporary file.
   */
  def scanWithTmpFile(implicit e: ExecutionContext): ClamParser[TemporaryFile]

  /**
   * Scans the file for virus without persisting.
   */
  def scanOnly(implicit e: ExecutionContext): ClamParser[Unit]

  def ping(implicit e: ExecutionContext): Future[String]

  def version(implicit e: ExecutionContext): Future[String]

  def stats(implicit e: ExecutionContext): Future[String]

}

abstract class BaseScanParser(
    sys: ActorSystem,
    mat: Materializer,
    config: Configuration
) extends ClammyScan {

  implicit val system: ActorSystem        = sys
  implicit val materializer: Materializer = mat

  val clamConfig = new ClamConfig(config)

  /**
   * Will validate the filename based on the configured regular expression
   * defined in application.conf.
   */
  protected def fileNameValid(filename: String): Boolean =
    clamConfig.validFilenameRegex.forall(
      regex =>
        regex.r.findFirstMatchIn(decode(filename, Codec.utf_8.charset)) match {
          case Some(m) => false
          case _       => true
      }
    )

  /**
   * Specifically sets up a `ClamSink` that is ready to receive the incoming
   * stream. Or one that is cancelled with success status.
   *
   * Controlled by the config property `clammyscan.scanDisabled`.
   */
  protected def clammySink(
      filename: String
  )(implicit ec: ExecutionContext) = {
    if (!clamConfig.scanDisabled) {
      ClamIO(
        host = clamConfig.host,
        port = clamConfig.port,
        timeout = clamConfig.timeout
      ).scan(filename)
    } else {
      // Scanning disabled
      cbpLogger.info(s"Scanning is disabled. $filename will not be scanned")
      ClamIO.cancelled(FileOk)
    }
  }

  /**
   * Determines the two sinks that are to be used for processing the stream.
   * The function will try to validate the filename against the
   * `clammyscan.validFilenameRegex` property if configured. In the case of an
   * invalid filename, two cancelled sinks are returned.
   */
  protected def sinks[A](filename: String, contentType: Option[String])(
      save: ToSaveSink[A]
  )(implicit ec: ExecutionContext): (ClamSink, SaveSink[A]) =
    if (fileNameValid(filename)) {
      (clammySink(filename), save(filename, contentType))
    } else {
      val errSave = Sink.cancelled[ByteString].mapMaterializedValue { _ =>
        Future.successful(None)
      }
      val errScan = ClamIO.cancelled(
        InvalidFilename(s"Filename $filename contains illegal characters")
      )

      (errScan, errSave)
    }

  /**
   * Graph that broadcasts each chunk from the incoming stream to both sinks,
   * before materializing the result from both into a `ScannedBody`.
   */
  protected def broadcastGraph[A](
      c: ClamSink,
      s: SaveSink[A]
  )(implicit e: ExecutionContext): Sink[ByteString, Future[ScannedBody[A]]] =
    Sink
      .fromGraph[ByteString, (Future[ScanResponse], Future[Option[A]])] {
        GraphDSL.create(c, s)((cs, ss) => (cs, ss)) { implicit b => (cs, ss) =>
          import GraphDSL.Implicits._

          val bro = b.add(Broadcast[ByteString](2))
          bro ~> cs
          bro ~> ss

          SinkShape(bro.in)
        }
      }
      .mapMaterializedValue { mat =>
        for {
          cr <- mat._1.recover {
                 case ClammyException(err)     => err
                 case stex: StreamTcpException => ScanError(stex.getMessage)
                 case ex =>
                   cbpLogger.error("", ex)
                   ScanError(unhandledException)
               }
          sr <- mat._2
        } yield ScannedBody(cr, sr)
      }

  /**
   * Function specifically for handling the ClamError cases in the
   * validation step. The logic here is highly dependent on how the
   * parser is configured.
   */
  protected def handleError[A](
      fud: MultipartFormData[ScannedBody[A]],
      err: ClamError
  )(
      remove: => Unit
  )(implicit ec: ExecutionContext): Future[Either[Result, ClamMultipart[A]]] = {
    Future.successful {
      err match {
        case vf: VirusFound =>
          // We have encountered the dreaded VIRUS...run awaaaaay
          if (clamConfig.canRemoveInfectedFiles) {
            remove
            Left(
              Results.NotAcceptable(
                Json.obj("message" -> vf.message)
              )
            )
          } else {
            // We cannot remove the uploaded file, so we return the parsed
            // result back to the controller to let it handle it.
            Right(fud)
          }

        case clamError =>
          if (clamConfig.canRemoveOnError) remove
          if (clamConfig.shouldFailOnError) {
            Left(
              Results.BadRequest(
                Json.obj("message" -> clamError.message)
              )
            )
          } else {
            Right(fud)
          }
      }
    }
  }

  /**
   * Execute a ping against clamd
   */
  def ping(implicit e: ExecutionContext): Future[String] =
    ClamIO(
      host = clamConfig.host,
      port = clamConfig.port,
      timeout = clamConfig.timeout
    ).ping

  /**
   * Get the clamd version string
   */
  def version(implicit e: ExecutionContext): Future[String] =
    ClamIO(
      host = clamConfig.host,
      port = clamConfig.port,
      timeout = clamConfig.timeout
    ).version

  /**
   * Get the clamd stats
   */
  def stats(implicit e: ExecutionContext): Future[String] =
    ClamIO(
      host = clamConfig.host,
      port = clamConfig.port,
      timeout = clamConfig.timeout
    ).stats

}

/**
 * Default implementation of the ClammyScan parsers
 */
class ClammyScanParser @Inject()(
    sys: ActorSystem,
    mat: Materializer,
    tempFileCreator: TemporaryFileCreator,
    bodyParsers: PlayBodyParsers,
    config: Configuration
) extends BaseScanParser(sys, mat, config) {

  def scan[A](
      save: ToSaveSink[A],
      remove: A => Unit
  )(implicit ec: ExecutionContext): ClamParser[A] = {
    bodyParsers
      .multipartFormData[ScannedBody[A]] {
        case FileInfo(partName, filename, contentType) =>
          val theSinks = sinks(filename, contentType)(save)
          val comb     = broadcastGraph(theSinks._1, theSinks._2)

          Accumulator(comb).map { ref =>
            MultipartFormData.FilePart(partName, filename, contentType, ref)
          }

      }
      .validateM((data: ClamMultipart[A]) => {
        data.files.headOption
          .map(
            hf =>
              hf.ref.scanResponse match {
                case err: ClamError =>
                  // Ooops...there seems to be a problem with the clamd result.
                  val maybeFile = data.files.headOption.flatMap(_.ref.maybeRef)
                  handleError(data, err) {
                    maybeFile.foreach(f => remove(f))
                  }
                case FileOk =>
                  Future.successful(Right(data))

            }
          )
          .getOrElse {
            Future.successful {
              Left(
                Results.BadRequest(
                  Json.obj(
                    "message" -> "Unable to locate any files after scan result"
                  )
                )
              )
            }
          }
      })
  }

  def scanWithTmpFile(
      implicit e: ExecutionContext
  ): ClamParser[TemporaryFile] =
    scan[TemporaryFile](
      save = { (fname, ctype) =>
        val tf = tempFileCreator.create("multipartBody", "scanWithTempFile")
        FileIO.toPath(tf.path).mapMaterializedValue { fio =>
          fio.map(_ => Option(tf))
        }
      },
      remove = tmpFile => tmpFile.delete()
    )

  def scanOnly(implicit ec: ExecutionContext): ClamParser[Unit] =
    scan[Unit](
      save = (f, c) =>
        Sink.cancelled[ByteString].mapMaterializedValue { notUsed =>
          Future.successful(None)
      },
      remove = _ => cbpLogger.debug("Only scanning, no file to remove")
    )

}
