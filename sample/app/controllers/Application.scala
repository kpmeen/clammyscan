package controllers

import javax.inject.Singleton

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.Inject
import net.scalytica.clammyscan.{ClammyScan, VirusFound}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._

@Singleton
class Application @Inject()(s: ActorSystem, m: Materializer) extends Controller
  with ClammyScan {

  implicit val system = s
  implicit val materializer = m

  val logger = Logger(this.getClass)

  /**
   * Testing streaming virus scanning of files with no persistence.
   */
  def scanFile = Action(scanOnly) { request =>
    request.body.files.head.ref._1 match {
      case Left(err) =>
        err match {
          case vf: VirusFound =>
            NotAcceptable(Json.obj("message" -> vf.message))

          case ce =>
            logger.error(s"An unknown error occured: ${ce.message}")
            InternalServerError(Json.obj("message" -> ce.message))
        }

      case Right(ok) =>
        Ok(Json.obj("message" -> "file is clean"))
    }
  }

  /**
   * Scan with temp file
   */
  def scanTempFile = Action(scanWithTmpFile) { request =>
    request.body.files.headOption.map { f =>
      val fname = f.ref._2.get.file.getName
      f.ref._1 match {
        case Left(err) =>
          Ok(
            Json.obj("message" -> s"$fname scan result was: ${err.message}")
          )

        case Right(fileOk) =>
          Ok(Json.obj("message" -> s"$fname uploaded successfully"))
      }
    }.getOrElse {
      BadRequest("could not find attached file")
    }
  }

}