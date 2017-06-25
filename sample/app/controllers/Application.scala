package controllers

import javax.inject.Singleton

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.Inject
import net.scalytica.clammyscan.{ClamError, ClammyScan, FileOk, VirusFound}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.ExecutionContext

@Singleton
class Application @Inject()(
    implicit actorSystem: ActorSystem,
    materializer: Materializer,
    ec: ExecutionContext,
    clammyScan: ClammyScan,
    val controllerComponents: ControllerComponents
) extends BaseController {

  val logger = Logger(this.getClass)

  logger.info("Temp directory is: " + System.getProperty("java.io.tmpdir"))

  /**
   * Testing streaming virus scanning of files with no persistence.
   */
  def scanFile = Action(clammyScan.scanOnly) { request =>
    request.body.files.head.ref.scanResponse match {
      case vf: VirusFound =>
        NotAcceptable(Json.obj("message" -> vf.message))

      case ce: ClamError =>
        logger.error(s"An unknown error occured: ${ce.message}")
        InternalServerError(Json.obj("message" -> ce.message))

      case FileOk =>
        Ok(Json.obj("message" -> "file is clean"))
    }
  }

  /**
   * Scan with temp file
   */
  def scanTempFile = Action(clammyScan.scanWithTmpFile) { request =>
    request.body.files.headOption.map { f =>
      val fname = f.ref.maybeRef.get.path.getFileName
      f.ref.scanResponse match {
        case err: ClamError =>
          Ok(Json.obj("message" -> s"$fname scan result was: ${err.message}"))

        case FileOk =>
          Ok(Json.obj("message" -> s"$fname uploaded successfully"))
      }
    }.getOrElse {
      BadRequest("could not find attached file")
    }
  }

}
