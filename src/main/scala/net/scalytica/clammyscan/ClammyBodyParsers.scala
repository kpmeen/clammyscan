package net.scalytica.clammyscan

import java.io.FileOutputStream
import java.net.{ConnectException, URLDecoder}

import play.api.Logger
import play.api.libs.Files.TemporaryFile
import play.api.libs.iteratee.Input.Empty
import play.api.libs.iteratee._
import play.api.libs.json.Json
import play.api.mvc.BodyParsers.parse._
import play.api.mvc._
import reactivemongo.api.gridfs.{DefaultFileToSave, GridFS, ReadFile}
import reactivemongo.bson._

import scala.concurrent.{ExecutionContext, Future}

/**
 * Enables streaming upload of files/attachments with custom metadata to GridFS
 */
trait ClammyBodyParsers extends ClammyParserConfig {
  self: Controller =>

  val cbpLogger = Logger(classOf[ClammyBodyParsers].getClass)

  /**
   * Mostly for convenience this. If you need a service for just scanning a file for infections, this is it.
   */
  def scanOnly(implicit ec: ExecutionContext): BodyParser[MultipartFormData[Either[ClamError, FileOk]]] =
    parse.using { request =>
      multipartFormData(Multipart.handleFilePart {
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
      })
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
   * S => Structure
   * R => Reader
   * W => Writer
   * Id => extends BSONValue
   */
  def scanAndParseAsGridFS[S, R[_], W[_], Id <: BSONValue](gfs: GridFS[S, R, W], fileName: Option[String] = None, metaData: Option[BSONDocument], allowDuplicates: Boolean = allowDuplicateFiles)
                                                          (fileExists: (String) => Boolean)
                                                          (implicit readFileReader: R[ReadFile[BSONValue]], sWriter: W[BSONDocument], ec: ExecutionContext) =
    parse.using { request =>

      val fileToSave = (fileName: String, contentType: Option[String]) => metaData.fold(DefaultFileToSave(fileName, contentType))(md => DefaultFileToSave(fileName, contentType, metadata = md))

      multipartFormData(Multipart.handleFilePart {
        case Multipart.FileInfo(partName, fname, contentType) => // TODO: Maybe override this to get greater control on exceptions?
          val fn = fileName.getOrElse(fname)
          if (fileNameValid(fn)) {
            if (!allowDuplicates && fileExists(fn)) {
              // If a file with the above query exists, abort the upload as we don't allow duplicates.
              cbpLogger.warn(s"File $fn already exists")
              // TODO: This must be improved to avoid causing the exception to be logged by the PlayDefaultUpstreamHandler.
              throw new DuplicateFileException(s"File $fn already exists")
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
            throw new InvalidFilenameException(s"Filename $fn contains illegal characters")
          }
      }
      ).validateM(futureData => Future.successful {
        val data = futureData
        data.files.headOption.map(f => f.ref._1 match {
          case Left(err) =>
            // Ooops...there seems to be a problem with the clamd scan result.
            val maybeFutureFile = Option(data.files.head.ref._2)
            err match {
              case vf: VirusFound =>
                // We have encountered the dreaded VIRUS...run awaaaaay
                if (canRemoveInfectedFiles) {
                  maybeFutureFile.map(theFile => theFile.map(f => gfs.remove(f.id)))
                }
                Left(NotAcceptable(Json.obj("message" -> vf.message)))
              case err: ScanError =>
                if (canRemoveOnError) {
                  maybeFutureFile.map(theFile => theFile.map(f => gfs.remove(f.id)))
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
        }).getOrElse {
          val errMsg = "Could not find file reference to in files list. This is bad..."
          cbpLogger.error(errMsg)
          Left(InternalServerError(Json.obj("message" -> errMsg)))
        }
      })
    }

  /**
   * Scans file for virus and buffers to a temporary file. Temp file is removed if file is infected.
   */
  def scanAndParseAsTempFile(implicit ec: ExecutionContext) = parse.using { request =>
    multipartFormData(Multipart.handleFilePart {
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
          throw new InvalidFilenameException(s"Filename $filename contains illegal characters")
        }
    }
    ).validateM(futureData => Future.successful {
      val data = futureData
      data.files.head.ref._1 match {
        case Left(err) =>
          // Ooops...there seems to be a problem with the clamd scan result.
          val temporaryFile = Option(data.files.head.ref._2)
          err match {
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
   * Will validate the filename based on the configured regular expression defined in application.conf.
   */
  private def fileNameValid(filename: String): Boolean = {
    validFilenameRegex.map(regex => regex.r.findFirstMatchIn(URLDecoder.decode(filename, Codec.utf_8.charset)) match {
      case Some(m) =>
        false
      case _ =>
        true
    }).getOrElse(true)
  }

}