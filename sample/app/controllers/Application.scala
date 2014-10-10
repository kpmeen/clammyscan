package controllers

import net.scalytica.clammyscan.{ClammyBodyParsers, VirusFound}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc._
import play.modules.reactivemongo._
import reactivemongo.api.gridfs._
import reactivemongo.bson._

import scala.concurrent._
import scala.concurrent.duration._

import Implicits.DefaultReadFileReader

object Application extends Controller with MongoController with ClammyBodyParsers {


  val logger = Logger(this.getClass)

  def gfs = GridFS(db)

  /**
   * Testing streaming file upload directly into GridFS using reactive mongo's gridFSBodyParser...
   */
  def upload(param1: String, param2: String) = Action.async(parse.using { rh =>
    val md = Some(BSONDocument("param1" -> BSONString(param1), "param2" -> BSONString(param2)))
    scanAndParseAsGridFS(gfs, metaData = md) { fn =>
      val query = BSONDocument(
        "filename" -> fn
      )
      Await.result(gfs.find(query).headOption.map(_.isDefined), 1 second)
    }
  }) { implicit request =>

    // Fetching the uploaded file...to verify it's OK to return Ok
    // NOTE: If you even get into the body of this controller...the file has successfully been uploaded. Jippi :-)
    futureGridFSFile.map(file => {
      // do something
      logger.info(s"Saved file with name ${file.filename}")
      Ok
    }).recover {
      case e: Throwable => InternalServerError(Json.obj("message" -> e.getMessage))
    }
  }

  /**
   * Testing streaming virus scanning of files with no persistence.
   */
  def scanFile = Action.async(scanOnly) { request =>
    request.body.files.head.ref.map {
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
  def scanTempFile = Action(scanAndParseAsTempFile) { request =>
    Ok(Json.obj("message" -> s"${request.body.files.head.ref._2.file.getName} uploaded successfully"))
  }

  /**
   * Serves up a file stored in a MongoDB GridFS collection with the given file name
   */
  def serveFile(fileName: String) = Action.async { request =>
    val file = gfs.find(BSONDocument("filename" -> fileName))
    serve(gfs, file)
  }

  def directUpload = Action(parse.temporaryFile) { request =>
    logger.info(s"Got file ${request.body.file.getName} from request")
    logger.info(s"With length: ${request.body.file.length()}")

    Ok(s"Got file ${request.body.file.getName} from request")
  }

}