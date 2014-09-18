package net.scalytica.clammyscan

import java.io.FileOutputStream
import java.net.{URLDecoder, ConnectException}

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
        case Multipart.FileInfo(partName, filename, contentType) =>
          if (!scanDisabled) {
            val socket = ClamSocket()
            if (socket.isConnected) {
              val clamav = new ClammyScan(socket)
              clamav.clamScan(filename)
            } else {
              if (!shouldFailOnError) {
                Done(Left(ScanError("Could not connect to clamd")), Empty)
              } else {
                throw new ConnectException("Could not connect to clamd")
              }
            }
          }
          else {
            cbpLogger.info(s"Scanning is disabled. $filename will not be scanned")
            Done(Right(FileOk()), Empty)
          }
      }
    }
  }

  /**
   * Gets a body parser that will save a file, with specified metadata and filename,
   * sent with multipart/form-data into the given GridFS store.
   *
   * First the stream is sent to both the ClamScan Iteratee and the GridFS Iteratee using Enumeratee.zip.
   * Then, when both Iteratees are done and none of them ended up in an Error state, the response is validated to check
   * for the presence of a ClamError. If this is found in the result, the file is removed from GridFS and
   * a JSON Result is returned.
   *
   * S => Structre
   * R => Reader
   * W => Writer
   * Id => extends BSONValue
   */
  def scanAndParseAsGridFS[S, R[_], W[_], Id <: BSONValue](gfs: GridFS[S, R, W], md: Map[String, BSONValue] = Map.empty, fname: Option[String] = None, allowDuplicates: Boolean = allowDuplicateFiles)
                                                          (implicit readFileReader: R[ReadFile[BSONValue]], sWriter: W[BSONDocument], ec: ExecutionContext) = parse.using { request =>
    def fileExists(fname: String): Boolean = {
      val query = BSONDocument("filename" -> fname) ++ createBSONMetadata(md, isQuery = true)
      Await.result(gfs.find(query).collect[List](), 1 seconds).nonEmpty
    }

    val metaData = createBSONMetadata(md)

    val fileToSave = (fileName: String, contentType: Option[String]) => DefaultFileToSave(fileName, contentType, metadata = metaData)

    multipartFormData {
      Multipart.handleFilePart {
        case Multipart.FileInfo(partName, filename, contentType) =>
          val fn = fname.getOrElse(filename)
          if (fileNameValid(fn)) {
            if (!allowDuplicates && fileExists(fn)) {
              // If a file with the above query exists, abort the upload as we don't allow duplicates.
              cbpLogger.warn(s"File $fn already exists")
              Enumeratee.zip(Done(Left(DuplicateFile(s"File $fn already exists")), Empty), Done(null, Empty))
            } else {
              // Prepare the GridFS iteratee...
              val git = gfs.iteratee(fileToSave(fn, contentType))
              if (!scanDisabled) {
                // Prepare the ClamScan iteratee
                val socket = ClamSocket()
                if (socket.isConnected) {
                  val clamav = new ClammyScan(socket)
                  val cav = clamav.clamScan(fn)
                  Enumeratee.zip(cav, git)
                } else {
                  if (!shouldFailOnError) {
                    Enumeratee.zip(Done(Left(ScanError("Could not connect to clamd")), Empty), git)
                  } else {
                    throw new ConnectException("Could not connect to clamd")
                  }
                }
              } else {
                cbpLogger.info(s"Scanning is disabled. $fn will not be scanned")
                Enumeratee.zip(Done(Right(FileOk()), Empty), git)
              }
            }
          } else {
            cbpLogger.warn(s"Filename $fn contains illegal characters")
            Enumeratee.zip(Done(Left(InvalidFilename(s"Filename $fn contains illegal characters")), Empty), Done(null, Empty))
          }
      }
    }.validateM(futureData => Future.successful {
      val data = futureData
      data.files.head.ref._1 match {
        case Left(err) =>
          // Ooops...there seems to be a problem with the clamd scan result.
          val maybeFutureFile = Option(data.files.head.ref._2)
          err match {
            case inv: InvalidFilename => Left(BadRequest(Json.obj("message" -> inv.message)))
            case dupe: DuplicateFile => Left(Conflict(Json.obj("message" -> dupe.message)))
            case vf: VirusFound =>
              // We have encountered the dreaded VIRUS...run awaaaaay
              if (canRemoveInfectedFiles) {
                  maybeFutureFile.map(theFile => Await.result(theFile.map(f => gfs.remove(f.id)), 120 seconds))
              }
              Left(NotAcceptable(Json.obj("message" -> vf.message)))
            case err: ScanError =>
              if (canRemoveOnError) {
                maybeFutureFile.map(theFile => Await.result(theFile.map(f => gfs.remove(f.id)), 120 seconds))
              }
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
   * Scans file for virus and buffers to a temporary file. Temp file is removed if file is infected.
   */
  def scanAndParseAsTempFile(implicit ec: ExecutionContext) = parse.using { request =>
    multipartFormData {
      Multipart.handleFilePart {
        case Multipart.FileInfo(partName, filename, contentType) =>
          if (fileNameValid(filename)) {
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
                val cav = clamav.clamScan(filename)
                Enumeratee.zip(cav, tfIte)
              } else {
                if (!shouldFailOnError) {
                  Enumeratee.zip(Done(Left(ScanError("Could not connect to clamd")), Empty), tfIte)
                } else {
                  throw new ConnectException("failOnError=true - throwing exception: Could not connect to clamd")
                }
              }
            } else {
              cbpLogger.info(s"Scanning is disabled. $filename will not be scanned")
              Enumeratee.zip(Done(Right(FileOk()), Empty), tfIte)
            }
          } else {
            cbpLogger.info(s"Filename $filename contains illegal characters")
            Enumeratee.zip(Done(Left(InvalidFilename(s"Filename $filename contains illegal characters")), Empty), Done(null, Empty))
          }
      }
    }.validateM(futureData => Future.successful {
      val data = futureData
      data.files.head.ref._1 match {
        case Left(err) =>
          // Ooops...there seems to be a problem with the clamd scan result.
          val temporaryFile = Option(data.files.head.ref._2)
          err match {
            case inv: InvalidFilename => Left(BadRequest(Json.obj("message" -> inv.message)))
            case vf: VirusFound =>
              // We have encountered the dreaded VIRUS...run awaaaaay
              if (canRemoveInfectedFiles) {
                temporaryFile.map(_.file.delete())
              }
              Left(NotAcceptable(Json.obj("message" -> vf.message)))
            case err: ScanError =>
              if (canRemoveOnError) {
                temporaryFile.map(_.file.delete())
              }
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

  /**
   * Will validate the filename based on the configured regular expression defined in application.conf.
   */
  private def fileNameValid(filename: String): Boolean = {
    validFilenameRegex.map(regex =>
      regex.r.findFirstMatchIn(URLDecoder.decode(filename, Codec.utf_8.charset)) match {
        case Some(m) =>
          false
        case _ =>
          true
      }
    ).getOrElse(true)
  }

}