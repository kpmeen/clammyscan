package net.scalytica.clammyscan

import java.net.URLDecoder.decode
import java.nio.file.Files

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import akka.util.ByteString
import com.google.inject.Inject
import net.scalytica.clammyscan.streams._
import play.api.libs.Files.{TemporaryFile, TemporaryFileCreator}
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.Results.{BadRequest, NotAcceptable}
import play.api.mvc._
import play.api.{Configuration, Logger}
import play.core.parsers.Multipart.FileInfo

import scala.concurrent.{ExecutionContext, Future}

/**
 * Enables parallel AV scanning of files/attachments being uploaded.
 */
trait ClammyScan {
  self =>

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
  def scanWithTmpFile(
      implicit exec: ExecutionContext
  ): ClamParser[TemporaryFile]

  /**
   * Scans the file for virus without persisting.
   */
  def scanOnly(implicit exec: ExecutionContext): ClamParser[Unit]

  def directScan[A](
      save: ToSaveSink[A],
      remove: A => Unit
  )(implicit exec: ExecutionContext): ChunkedClamParser[A]

  def directScanWithTmpFile(
      implicit exec: ExecutionContext
  ): ChunkedClamParser[TemporaryFile]

  def directScanOnly(implicit exec: ExecutionContext): ChunkedClamParser[Unit]

  def ping(implicit exec: ExecutionContext): Future[String]

  def version(implicit exec: ExecutionContext): Future[String]

  def stats(implicit exec: ExecutionContext): Future[String]

}

abstract class BaseScanParser(
    sys: ActorSystem,
    mat: Materializer,
    config: Configuration
) extends ClammyScan {

  implicit val system: ActorSystem        = sys
  implicit val materializer: Materializer = mat

  val clamConfig = new ClamConfig(config.underlying)

  /**
   * Will validate the filename based on the configured regular expression
   * defined in application.conf.
   */
  protected def fileNameValid(filename: String): Boolean =
    clamConfig.validFilenameRegex.forall(
      regex =>
        regex.r.findFirstMatchIn(decode(filename, Codec.utf_8.charset)) match {
          case Some(_) => false
          case None    => true
      }
    )

  /**
   * Specifically sets up a `ClamSink` that is ready to receive the incoming
   * stream. Or one that is cancelled with success status.
   *
   * Controlled by the config property `clammyscan.scanDisabled`.
   */
  protected def clammySink(filename: String): ClamSink = {
    if (!clamConfig.scanDisabled) {
      ClamIO(clamConfig).scan(filename)
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
  ): (ClamSink, SaveSink[A]) =
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

  private def exceptionRecovery: PartialFunction[Throwable, ScanError] = {
    case cex: ClammyException =>
      cex.scanError

    case stex: StreamTcpException =>
      ScanError(stex.getMessage)

    case ex =>
      cbpLogger.error("An unhandled exception occurred", ex)
      ScanError(UnhandledException)
  }

  /**
   * Graph that broadcasts each chunk from the incoming stream to both sinks,
   * before materializing the result from both into a `ScannedBody`.
   */
  protected def broadcastGraph[A](
      c: ClamSink,
      s: SaveSink[A]
  )(
      implicit exec: ExecutionContext
  ): Sink[ByteString, Future[ScannedBody[A]]] = {
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
          cr <- mat._1.recover(exceptionRecovery)
          sr <- mat._2
        } yield ScannedBody(cr, sr)
      }
  }

  /**
   * Function specifically for handling the ClamError cases in the
   * validation step. The logic here is highly dependent on how the
   * parser is configured.
   */
  protected def handleError[A](
      res: A,
      scanRes: ScanResponse
  )(
      remove: => Unit
  ): Either[Result, A] = {
    scanRes match {
      case vf: VirusFound =>
        // We have encountered the dreaded VIRUS...run awaaaaay
        if (clamConfig.canRemoveInfectedFiles) {
          remove
          Left(NotAcceptable(Json.obj("message" -> vf.message)))
        } else {
          // We cannot remove the uploaded file, so we return the parsed
          // result back to the controller to let it handle it.
          Right(res)
        }

      case clamError: ClamError =>
        if (clamConfig.canRemoveOnError) remove
        if (clamConfig.shouldFailOnError) {
          Left(BadRequest(Json.obj("message" -> clamError.message)))
        } else {
          Right(res)
        }

      case _ => Right(res)
    }
  }

  /**
   * Execute a ping against clamd
   */
  def ping(implicit exec: ExecutionContext): Future[String] =
    ClamIO(clamConfig).ping

  /**
   * Get the clamd version string
   */
  def version(implicit exec: ExecutionContext): Future[String] =
    ClamIO(clamConfig).version

  /**
   * Get the clamd stats
   */
  def stats(implicit exec: ExecutionContext): Future[String] =
    ClamIO(clamConfig).stats

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
          val (clamSink, saveSink) = sinks(filename, contentType)(save)
          val comb                 = broadcastGraph(clamSink, saveSink)

          Accumulator(comb).map { ref =>
            MultipartFormData.FilePart(partName, filename, contentType, ref)
          }

      }
      .validateM { (data: ClamMultipart[A]) =>
        data.files.headOption
          .map { hf =>
            hf.ref.scanResponse match {
              case err: ClamError =>
                val maybeFile = data.files.headOption.flatMap(_.ref.maybeRef)
                Future.successful {
                  handleError(data, err)(maybeFile.foreach(remove))
                }

              case FileOk =>
                Future.successful(Right(data))

            }
          }
          .getOrElse {
            Future.successful {
              Left(
                BadRequest(
                  Json.obj(
                    "message" -> "Unable to locate any files after scan result"
                  )
                )
              )
            }
          }
      }
  }

  def scanWithTmpFile(
      implicit exec: ExecutionContext
  ): ClamParser[TemporaryFile] = scan[TemporaryFile](
    save = { (_, _) =>
      val tf = tempFileCreator.create("multipartBody", "scanWithTempFile")
      FileIO.toPath(tf.path).mapMaterializedValue(_.map(_ => Option(tf)))
    },
    remove = tmpFile => tmpFile.delete()
  )

  def scanOnly(implicit ec: ExecutionContext): ClamParser[Unit] = scan[Unit](
    save = (_, _) => {
      Sink
        .cancelled[ByteString]
        .mapMaterializedValue(_ => Future.successful(None))
    },
    remove = _ => cbpLogger.debug("Only scanning, no file to remove")
  )

  def directScan[A](
      save: ToSaveSink[A],
      remove: A => Unit
  )(implicit exec: ExecutionContext): ChunkedClamParser[A] = BodyParser { rh =>
    val fileName         = rh.getQueryString("filename").getOrElse("no_name")
    val maybeContentType = rh.getQueryString("contentType")

    val theSinks = sinks(fileName, maybeContentType)(save)
    val comb     = broadcastGraph(theSinks._1, theSinks._2)

    Accumulator(comb).map { sb =>
      sb.scanResponse match {
        case FileOk => Right(sb)
        case e      => handleError(sb, e)(sb.maybeRef.foreach(f => remove(f)))
      }
    }
  }

  def directScanWithTmpFile(
      implicit exec: ExecutionContext
  ): ChunkedClamParser[TemporaryFile] = directScan[TemporaryFile](
    save = (_, _) => {
      val tempFile = tempFileCreator.create("requestBody", "asTemporaryFile")
      val s = StreamConverters
        .fromOutputStream(() => Files.newOutputStream(tempFile.toPath))
        .mapMaterializedValue(_.map(_ => Option(tempFile)))
      s
    },
    remove = tmpFile => tmpFile.delete()
  )

  def directScanOnly(
      implicit exec: ExecutionContext
  ): ChunkedClamParser[Unit] = directScan[Unit](
    save = (_, _) => {
      Sink
        .cancelled[ByteString]
        .mapMaterializedValue(_ => Future.successful(None))
    },
    remove = _ => cbpLogger.debug("Only scanning, no file to remove")
  )

}
