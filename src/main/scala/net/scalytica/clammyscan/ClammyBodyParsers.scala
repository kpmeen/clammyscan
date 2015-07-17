package net.scalytica.clammyscan

import java.net.{ConnectException, URLDecoder}

import play.api.Logger
import play.api.libs.Files.TemporaryFile
import play.api.libs.iteratee._
import play.api.libs.json.Json
import play.api.mvc.BodyParsers.parse._
import play.api.mvc._
import play.core.parsers.Multipart

import scala.concurrent.{ExecutionContext, Future}

/**
 * Enables streaming upload of files/attachments with custom metadata to GridFS
 */
trait ClammyBodyParsers extends ClammyParserConfig {
  self: Controller =>

  val cbpLogger = Logger(classOf[ClammyBodyParsers])

  private val CouldNotConnect = ScanError("Could not connect to clamd")

  type ClamResponse = Either[ClamError, FileOk]

  def scan[A](save: (String, Option[String]) => Iteratee[Array[Byte], A], remove: A => Unit)(implicit ec: ExecutionContext): BodyParser[MultipartFormData[(Future[ClamResponse], A)]] =
    parse.using { request =>
      multipartFormData(Multipart.handleFilePart {
        case Multipart.FileInfo(partName, filename, contentType) =>
          if (fileNameValid(filename)) {
            val fite = save(filename, contentType)
            if (!scanDisabled) {
              // Scan with clammy
              val socket = ClamSocket()
              if (socket.isConnected) {
                val clamav = new ClammyScan(socket)
                val cav = clamav.clamScan(filename)
                Enumeratee.zip(cav, fite)
              } else {
                failedConnection(Enumeratee.zip(Done(Future.successful(Left(CouldNotConnect)), Input.EOF), fite))
              }
            } else {
              cbpLogger.info(s"Scanning is disabled. $filename will not be scanned")
              Enumeratee.zip(Done(Future.successful(Right(FileOk())), Input.EOF), fite)
            }
          } else {
            cbpLogger.info(s"Filename $filename contains illegal characters")
            throw new InvalidFilenameException(s"Filename $filename contains illegal characters")
          }
      }).validateM(futureData => {
        val data = futureData
        data.files.head.ref._1.flatMap {
          case Left(err) =>
            // Ooops...there seems to be a problem with the clamd scan result.
            val maybeFile = data.files.headOption.map(_.ref._2)
            handleError(futureData, err) {
              maybeFile.foreach(f => remove(f))
            }
          case Right(ok) =>
            // It's all good...
            Future.successful(Right(futureData))
        }
      })
    }

  /**
   * Scans file for virus and buffers to a temporary file. Temp file is removed if file is infected.
   */
  def scanWithTempFile(implicit ec: ExecutionContext): BodyParser[MultipartFormData[(Future[ClamResponse], TemporaryFile)]] =
    scan[TemporaryFile](
      save = { (fname, ctype) =>
        val tempFile = TemporaryFile("multipartBody", "scanWithTempFile")
        Iteratee.fold[Array[Byte], java.io.FileOutputStream](new java.io.FileOutputStream(tempFile.file)) { (os, data) =>
          os.write(data)
          os
        }.map { os =>
          os.close()
          tempFile.file.deleteOnExit()
          tempFile
        }
      },
      remove = { tmpFile =>
        tmpFile.file.delete()
      }
    )

  /**
   * Mostly for convenience this. If you need a service for just scanning a file for infections, this is it.
   */
  def scanOnly(implicit ec: ExecutionContext): BodyParser[MultipartFormData[(Future[ClamResponse], Unit)]] =
    scan[Unit](
      save = { (fname, ctype) => Done(()) },
      remove = { _ => cbpLogger.debug("Only scanning, no file to remove") }
    )

  /**
   * Function specifically for handling the ClamError cases in the validation step
   */
  private def handleError[A](fud: MultipartFormData[(Future[ClamResponse], A)], err: ClamError)(onError: => Unit)(implicit ec: ExecutionContext) = {
    err match {
      case vf: VirusFound =>
        // We have encountered the dreaded VIRUS...run awaaaaay
        if (canRemoveInfectedFiles) {
          temporaryFile.map(_.file.delete())
        }
        Future.successful(Left(NotAcceptable(Json.obj("message" -> vf.message))))
      case err: ScanError =>
        if (canRemoveOnError) {
          onError
        }
        if (shouldFailOnError) {
          Future.successful(Left(BadRequest(Json.obj("message" -> "File size exceeds maximum file size limit."))))
        } else {
          Future.successful(Right(fud))
        }
    }
  }

  /**
   * Error handling for when the connection to ClamAV cannot be established
   */
  @throws(classOf[ConnectException])
  private def failedConnection[A, B](a: Iteratee[A, B]): Iteratee[A, B] = {
    if (!shouldFailOnError) a else throw new ConnectException(CouldNotConnect.message)
  }

  /**
   * Will validate the filename based on the configured regular expression defined in application.conf.
   */
  private def fileNameValid(filename: String): Boolean = {
    validFilenameRegex.map(regex => regex.r.findFirstMatchIn(URLDecoder.decode(filename, Codec.utf_8.charset)) match {
      case Some(m) => false
      case _ => true
    }).getOrElse(true)
  }

}