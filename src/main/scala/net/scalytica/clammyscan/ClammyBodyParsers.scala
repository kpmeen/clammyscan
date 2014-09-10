package net.scalytica.clammyscan

import java.io.FileOutputStream
import java.net.ConnectException

import play.api.Logger
import play.api.libs.Files.TemporaryFile
import play.api.libs.iteratee.Input.Empty
import play.api.libs.iteratee._
import play.api.libs.json.Json
import play.api.mvc.BodyParsers.parse._
import play.api.mvc._
import reactivemongo.api.gridfs.{DefaultFileToSave, GridFS, ReadFile}
import reactivemongo.bson._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Enables streaming upload of files/attachments with custom metadata to GridFS
 */
trait ClammyBodyParsers extends ClammyParserConfig {
  self: Controller =>

  val cbpLogger = Logger(classOf[ClammyBodyParsers].getClass)

  /**
   * Mostly for convenience this. If you need a service for just scanning a file for infections, this is it.
   */
  def scanOnly(implicit ec: ExecutionContext): BodyParser[MultipartFormData[Either[ClamError, FileOk]]] = parse.using { request =>
    multipartFormData {
      Multipart.handleFilePart {
        case Multipart.FileInfo(part, fname, ctype) =>
          if (!scanDisabled) {
            val socket = ClamSocket()
            if (socket.isConnected) {
              val clamav = new ClammyScan(socket)
              clamav.clamScan(fname)
            } else {
              if (!shouldFailOnError) {
                Done(Left(ScanError("Could not connect to clamd")), Empty)
              } else {
                throw new ConnectException("Could not connect to clamd")
              }
            }
          }
          else {
            cbpLogger.info(s"Scanning is disabled. $fname will not be scanned")
            Done(Right(FileOk()), Empty)
          }
      }
    }
  }

  /**
   * Gets a body parser that will save a file, with specified metadata and filename,
   * sent with multipart/form-data into the given GridFS store.
   */
  def scanAndParseAsGridFS[Structure, Reader[_], Writer[_], Id <: BSONValue](gfs: GridFS[Structure, Reader, Writer], fname: String, md: Map[String, BSONValue] = Map.empty)
                                                                            (implicit readFileReader: Reader[ReadFile[BSONValue]], sWriter: Writer[BSONDocument], ec: ExecutionContext) = parse.using { request =>
    def fileExists: Boolean = {
      val query = BSONDocument("filename" -> fname) ++ createBSONMetadata(md, isQuery = true)
      Await.result(gfs.find(query).collect[List](), 1 seconds).nonEmpty
    }

    if (!allowDuplicateFiles && fileExists) {
      // If a file with the above query exists, abort the upload as we don't allow duplicates.
      cbpLogger.warn(s"File $fname already exists")
      parse.error(Future.successful(Conflict(Json.obj("message" -> s"Filename $fname already exists."))))
    } else {
      /*
        This is where the magic happens...

        First the stream is sent to both the ClamScan Iteratee and the GridFS Iteratee using Enumeratee.zip.
        Then, when both Iteratees are done and none of them ended up in an Error state, the response is validated to check
        for the presence of a ClamError. If this is found in the result, the file is removed from GridFS and
        a JSON Result is returned.
       */
      val metaData = createBSONMetadata(md)

      val fileToSave = (fileName: String, contentType: Option[String]) => DefaultFileToSave(fileName, contentType, metadata = metaData)

      multipartFormData {
        Multipart.handleFilePart {
          case Multipart.FileInfo(partName, filename, contentType) =>
            val git = gfs.iteratee(fileToSave(filename, contentType))
            if (!scanDisabled) {
              val socket = ClamSocket()
              if (socket.isConnected) {
                val clamav = new ClammyScan(socket)
                val cav = clamav.clamScan(filename)
                Enumeratee.zip(cav, git)
              } else {
                if (!shouldFailOnError) {
                  Enumeratee.zip(Done(Left(ScanError("Could not connect to clamd")), Empty), git)
                } else {
                  throw new ConnectException("Could not connect to clamd")
                }
              }
            } else {
              cbpLogger.info(s"Scanning is disabled. $fname will not be scanned")
              Enumeratee.zip(Done(Right(FileOk()), Empty), git)
            }
        }
      }.validateM(futureData => Future.successful {
        val data = futureData
        data.files.head.ref._1 match {
          case Left(err) =>
            // Ooops...there seems to be a problem with the clamd scan result.
            val futureFile = data.files.head.ref._2
            err match {
              case vf: VirusFound =>
                // We have encountered the dreaded VIRUS...run awaaaaay
                if (canRemoveInfectedFiles) Await.result(futureFile.map(theFile => gfs.remove(theFile.id)), 120 seconds)
                Left(NotAcceptable(Json.obj("message" -> s"file $fname contained a virus: ${vf.message}")))
              case err: ClamError =>
                // in MongoDB so we could pick up and do a background scan later if necessary.
                if (canRemoveOnError) Await.result(futureFile.map(theFile => gfs.remove(theFile.id)), 120 seconds)
                if (shouldFailOnError) {
                  Left(BadRequest(Json.obj("message" -> "File size exceeds maximum file size limit.")))
                } else {
                  Right(futureData)
                }
            }
          case Right(ok) =>
            // It's all good...
            Right(futureData)
        }
      })
    }
  }


  /**
   * Scans file for virus and buffers to a temporary file. Temp file is removed if file is infected.
   */
  def scanAndParseAsTempFile(implicit ec: ExecutionContext) = parse.using { request =>
    multipartFormData {
      Multipart.handleFilePart {
        case Multipart.FileInfo(pname, fname, ctype) =>
          val tempFile = TemporaryFile("multipartBody", "asTemporaryFile")
          val tfIte = Iteratee.fold[Array[Byte], FileOutputStream](new java.io.FileOutputStream(tempFile.file)) { (os, data) =>
            os.write(data)
            os
          }.map { os =>
            os.close()
            tempFile
          }
          if (!scanDisabled) {
            val socket = ClamSocket()
            if (socket.isConnected) {
              val clamav = new ClammyScan(socket)
              val cav = clamav.clamScan(fname)
              Enumeratee.zip(cav, tfIte)
            } else {
              if (!shouldFailOnError) {
                Enumeratee.zip(Done(Left(ScanError("Could not connect to clamd")), Empty), tfIte)
              } else {
                throw new ConnectException("failOnError=true - throwing exception: Could not connect to clamd")
              }
            }
          } else {
            cbpLogger.info(s"Scanning is disabled. $fname will not be scanned")
            Enumeratee.zip(Done(Right(FileOk()), Empty), tfIte)
          }
      }
    }.validateM(futureData => Future.successful {
      val data = futureData
      data.files.head.ref._1 match {
        case Left(err) =>
          // Ooops...there seems to be a problem with the clamd scan result.
          val temporaryFile = data.files.head.ref._2
          err match {
            case vf: VirusFound =>
              // We have encountered the dreaded VIRUS...run awaaaaay
              if (canRemoveInfectedFiles) temporaryFile.file.delete()
              Left(NotAcceptable(Json.obj("message" -> s"file ${temporaryFile.file.getName} contained a virus: ${vf.message}")))
            case err: ClamError =>
              /*
                The most common reason for this to happen is if clamd isn't properly configured...however, it may occur
                if for some reason clam isn't running. How to differentiate? It's either up or down...we don't know yet.
                Anyway, shame on you for not configuring clamd to accept streams large enough to handle the max upload
                size.
              */
              // in MongoDB so we could pick up and do a background scan later if necessary.
              if (canRemoveOnError) temporaryFile.file.delete()
              if (shouldFailOnError) {
                Left(BadRequest(Json.obj("message" -> "File size exceeds maximum file size limit.")))
              } else {
                Right(futureData)
              }
          }
        case Right(ok) =>
          // It's all good...
          Right(futureData)
      }
    })
  }

  /**
   * Convenience function for retrieving the actual FilePart from the request, after scanning and
   * saving has been completed.
   *
   * Since the scan results have been validated as part of the parsing, we can be sure that the it passed through
   * successfully.
   */
  def futureGridFSFile(implicit request: Request[MultipartFormData[(Either[ClamError, FileOk], Future[ReadFile[BSONValue]])]]) = {
    request.body.files.head.ref._2
  }

  /**
   * Takes a Map containing the custom metadata to be stored with the file.
   *
   * @param md Map[String, String] containing the custom metadata
   * @return a BSONDocument representing
   */
  private def createBSONMetadata(md: Map[String, BSONValue], isQuery: Boolean = false): BSONDocument = {
    var tmp = BSONDocument()
    md.map(m => tmp = tmp ++ BSONDocument((if (isQuery) s"metadata.${m._1}" else m._1) -> m._2))
    tmp
  }

}