import net.scalytica.clammyscan.{InvalidFilenameException, DuplicateFileException}
import play.api._
import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc._

import scala.concurrent.Future


object Global extends GlobalSettings {

  override def onError(request: RequestHeader, ex: Throwable) = {
    ex match {
      case t: Throwable if t.getCause != null =>
        t.getCause match {
          case dfe: DuplicateFileException => Future.successful(Conflict(Json.obj("message" -> dfe.message)))
          case ife: InvalidFilenameException => Future.successful(BadRequest(Json.obj("message" -> ife.message)))
          case _ => Future.successful(InternalServerError(Json.obj("message" -> t.getMessage)))
        }
      case ise: Throwable => Future.successful(InternalServerError(Json.obj("message" -> ise.getMessage)))
    }
  }


}
