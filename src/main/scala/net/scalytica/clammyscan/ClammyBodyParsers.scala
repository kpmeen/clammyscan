package net.scalytica.clammyscan

import java.io.FileOutputStream
import java.net.{ConnectException, URLDecoder}

import play.api.Logger
import play.api.libs.Files.TemporaryFile
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

  val cbpLogger = Logger(classOf[ClammyBodyParsers])

  type ClamResponse = Either[ClamError, FileOk]
  type ClammyGridFSBody = (Future[ClamResponse], Future[ReadFile[BSONValue]])

  /**
   * Mostly for convenience this. If you need a service for just scanning a file for infections, this is it.
   */
  def scanOnly(implicit ec: ExecutionContext): BodyParser[MultipartFormData[Future[ClamResponse]]] =
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
                Done(Future.successful(Left(ScanError("Could not connect to clamd"))), Input.EOF)
              } else {
                throw new ConnectException("Could not connect to clamd")
              }
            }
          }
          else {
            cbpLogger.info(s"Scanning is disabled. $filename will not be scanned")
            Done(Future.successful(Right(FileOk())), Input.EOF)
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
                                                          (implicit readFileReader: R[ReadFile[BSONValue]], sWriter: W[BSONDocument], ec: ExecutionContext): BodyParser[MultipartFormData[ClammyGridFSBody]] =
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
                    Enumeratee.zip(Done(Future.successful(Left(ScanError("Could not connect to clamd"))), Input.EOF), git)
                  } else {
                    throw new ConnectException("Could not connect to clamd")
                  }
                }
              } else {
                cbpLogger.info(s"Scanning is disabled. $fn will not be scanned")
                Enumeratee.zip(Done(Future.successful(Right(FileOk())), Input.EOF), git)
              }
            }
          } else {
            cbpLogger.warn(s"Filename $fn contains illegal characters")
            throw new InvalidFilenameException(s"Filename $fn contains illegal characters")
          }
      }).validateM(futureData => {
        val data = futureData
        data.files.headOption.map(ef => ef.ref._1.flatMap {
          case Left(err) =>
            // Ooops...there seems to be a problem with the clamd scan result.
            val maybeFutureFile = Option(data.files.head.ref._2)
            err match {
              case vf: VirusFound =>
                // We have encountered the dreaded VIRUS...run awaaaaay
                if (canRemoveInfectedFiles) {
                  maybeFutureFile.map(theFile => theFile.map(f => gfs.remove(f.id)))
                }
                Future.successful(Left(NotAcceptable(Json.obj("message" -> vf.message))))
              case err: ScanError =>
                if (canRemoveOnError) {
                  maybeFutureFile.map(theFile => theFile.map(f => gfs.remove(f.id)))
                }
                if (shouldFailOnError) {
                  Future.successful(Left(BadRequest(Json.obj("message" -> "File size exceeds maximum file size limit."))))
                } else {
                  Future.successful(Right(futureData))
                }
            }
          case Right(ok) =>
            // It's all good...
            Future.successful(Right(futureData))
        }).getOrElse {
          val errMsg = "Could not find file reference to in files list. This is bad..."
          cbpLogger.error(errMsg)
          Future.successful(Left(InternalServerError(Json.obj("message" -> errMsg))))
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
                Enumeratee.zip(Done(Future.successful(Left(ScanError("Could not connect to clamd"))), Input.EOF), tfIte)
              } else {
                throw new ConnectException("failOnError=true - throwing exception: Could not connect to clamd")
              }
            }
          } else {
            cbpLogger.info(s"Scanning is disabled. $filename will not be scanned")
            Enumeratee.zip(Done(Future.successful(Right(FileOk())), Input.EOF), tfIte)
          }
        } else {
          /*
            TODO: This bit should really be handled differently.The current implementation will log the exception as an
            [error] severity in the log files...which it isn't...at most it's a warning. Reason being that it's the
            PlayDefaultUpstramHandler that will actually end up catching this exception (due to the execution context
            of the BodyParser), log it and send it to the Play Global.onError implementation.
          */
          cbpLogger.info(s"Filename $filename contains illegal characters")
          throw new InvalidFilenameException(s"Filename $filename contains illegal characters")
        }
    }).validateM(futureData => {
      val data = futureData
      data.files.head.ref._1.flatMap {
        case Left(err) =>
          // Ooops...there seems to be a problem with the clamd scan result.
          val temporaryFile = Option(data.files.head.ref._2)
          err match {
            case vf: VirusFound =>
              // We have encountered the dreaded VIRUS...run awaaaaay
              if (canRemoveInfectedFiles) {
                temporaryFile.map(_.file.delete())
              }
              Future.successful(Left(NotAcceptable(Json.obj("message" -> vf.message))))
            case err: ScanError =>
              if (canRemoveOnError) {
                temporaryFile.map(_.file.delete())
              }
              if (shouldFailOnError) {
                Future.successful(Left(BadRequest(Json.obj("message" -> "File size exceeds maximum file size limit."))))
              } else {
                Future.successful(Right(futureData))
              }
          }
        case Right(ok) =>
          // It's all good...
          Future.successful(Right(futureData))
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
  def futureGridFSFile(implicit request: Request[MultipartFormData[ClammyGridFSBody]]) = {
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