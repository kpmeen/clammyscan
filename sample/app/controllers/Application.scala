package controllers

import javax.inject.Singleton
import com.google.inject.Inject
import net.scalytica.clammyscan._
import net.scalytica.clammyscan.streams.{
  ClamError,
  FileOk,
  ScannedBody,
  VirusFound
}
import play.api.Logger
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.ExecutionContext

@Singleton
class Application @Inject()(
    implicit ec: ExecutionContext,
    clammyScan: ClammyScan,
    val controllerComponents: ControllerComponents
) extends BaseController {

  val logger = Logger(this.getClass)

  logger.info("Temp directory is: " + System.getProperty("java.io.tmpdir"))

  /**
   * Testing streaming virus scanning of multipart file with no persistence.
   */
  def scanFile: Action[ClamMultipart[Unit]] =
    Action(clammyScan.scanOnly) { request =>
      request.body.files.head.ref.scanResponse match {
        case vf: VirusFound =>
          NotAcceptable(Json.obj("message" -> vf.message))

        case ce: ClamError =>
          logger.error(s"An unknown error occurred: ${ce.message}")
          InternalServerError(Json.obj("message" -> ce.message))

        case FileOk =>
          Ok(Json.obj("message" -> "file is clean"))
      }
    }

  /**
   * Scan and store multipart as temp file
   */
  def scanTempFile: Action[ClamMultipart[Files.TemporaryFile]] =
    Action(clammyScan.scanWithTmpFile) { request =>
      request.body.files.headOption
        .map { f =>
          val fname = f.ref.maybeRef.get.path.getFileName
          f.ref.scanResponse match {
            case err: ClamError =>
              Ok(
                Json.obj("message" -> s"$fname scan result was: ${err.message}")
              )

            case FileOk =>
              Ok(Json.obj("message" -> s"$fname uploaded successfully"))
          }
        }
        .getOrElse {
          BadRequest("could not find attached file")
        }
    }

  /**
   * Scanning of request body and store as temporary file
   */
  def directTempFile: Action[ScannedBody[Files.TemporaryFile]] =
    Action(clammyScan.directScanWithTmpFile) { request =>
      request.body.maybeRef
        .map { ref =>
          val fname = ref.path.getFileName
          request.body.scanResponse match {
            case err: ClamError =>
              Ok(
                Json.obj("message" -> s"$fname scan result was: ${err.message}")
              )

            case FileOk =>
              Ok(Json.obj("message" -> s"$fname uploaded successfully"))
          }
        }
        .getOrElse {
          Ok(Json.obj("message" -> s"Request did not contain any files"))
        }
    }

  def directScanFile: Action[ScannedBody[Unit]] =
    Action(clammyScan.directScanOnly) { request =>
      request.body.scanResponse match {
        case vf: VirusFound =>
          NotAcceptable(Json.obj("message" -> vf.message))

        case ce: ClamError =>
          logger.error(s"An unknown error occurred: ${ce.message}")
          InternalServerError(Json.obj("message" -> ce.message))

        case FileOk =>
          Ok(Json.obj("message" -> "file is clean"))
      }
    }

}
