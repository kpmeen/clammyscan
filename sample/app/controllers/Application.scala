package controllers

import javax.inject.Singleton

import net.scalytica.clammyscan.{ClammyBodyParsers, VirusFound}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._

@Singleton
class Application extends Controller with ClammyBodyParsers {

  val logger = Logger(this.getClass)

  /**
   * Testing streaming virus scanning of files with no persistence.
   */
  def scanFile = Action.async(scanOnly) { request =>
    request.body.files.head.ref._1.map {
      case Left(err) =>
        err match {
          case vf: VirusFound => NotAcceptable(Json.obj("message" -> vf.message))
          case ce => InternalServerError(Json.obj("message" -> ce.message))
        }
      case Right(ok) =>
        Ok(Json.obj("message" -> "file is clean"))
    }
  }

  /**
   * Scan with temp file
   */
  def scanTempFile = Action(scanWithTempFile) { request =>
    Ok(Json.obj("message" -> s"${request.body.files.head.ref._2.file.getName} uploaded successfully"))
  }

}