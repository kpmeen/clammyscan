package controllers

import javax.inject.Singleton

import akka.actor.ActorSystem
import com.google.inject.Inject
import net.scalytica.clammyscan.{ClammyBodyParsers, VirusFound}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._

@Singleton
class Application @Inject()(s: ActorSystem) extends Controller
  with ClammyBodyParsers {

  val system = s

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
    val fname = request.body.files.head.ref._2.get.file.getName
    Ok(Json.obj("message" -> s"$fname uploaded successfully"))
  }

}