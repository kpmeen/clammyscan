package net.scalytica.clammyscan

import java.io.File

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import org.apache.commons.io.output.ByteArrayOutputStream
import org.apache.http.HttpEntity
import org.apache.http.entity.ContentType
import org.apache.http.entity.mime.{MultipartEntity, MultipartEntityBuilder}
import org.apache.http.entity.mime.content.{FileBody, StringBody}
import org.scalatest.Matchers.fail
import play.api.http.Writeable
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{AnyContentAsMultipartFormData, Codec, MultipartFormData}

import scala.concurrent.Future

object TestHelpers {

  val instreamCmd = "zINSTREAM\u0000"
  val pingCmd = "zPING\u0000"
  val statusCmd = "zSTATS\u0000"
  val versionCmd = "VERSION"

  val eicarString = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-" +
    "ANTIVIRUS-TEST-FILE!$H+H*\u0000"

  val eicarStrSource = Source.single[ByteString](ByteString(eicarString))

  val cleanFile = file("clean.pdf")
  val eicarFile = file("eicar.com")
  val eicarTxtFile = file("eicar.com.txt")
  val eicarZipFile = file("eicarcom2.zip")

  val cleanFileSrc = fileAsSource("clean.pdf")
  val eicarFileSrc = fileAsSource("eicar.com")
  val eicarTxtFileSrc = fileAsSource("eicar.com.txt")
  val eicarZipFileSrc = fileAsSource("eicarcom2.zip")

  def file(fname: String): File =
    new File(this.getClass.getResource(s"/$fname").toURI)

  def fileAsSource(fname: String): Source[ByteString, Future[IOResult]] =
    FileIO.fromFile(file(fname))

  def unexpectedClamError(ce: ClamError): Nothing =
    fail(s"Unexpected ClamError result ${ce.message}")

  object MultipartWriteable {

    implicit def writableOfMultiPartFormData(
      implicit
      codec: Codec
    ): Writeable[MultipartFormData[TemporaryFile]] = {

      val builder = MultipartEntityBuilder.create()

      var entity: Option[HttpEntity] = None

      def transform(multipart: MultipartFormData[TemporaryFile]) = {

        multipart.dataParts.foreach { part =>
          part._2.foreach { p2 =>
            builder.addPart(part._1, new StringBody(p2))
          }
        }

        multipart.files.foreach { file =>
          val part = new FileBody(
            file.ref.file,
            ContentType.create(
              file.contentType.getOrElse("application/octet-stream")
            ),
            file.filename
          )
          builder.addPart(file.key, part)
        }

        entity = Some(builder.build())
        val outputStream = new ByteArrayOutputStream
        entity.foreach(_.writeTo(outputStream))
        val bytes = outputStream.toByteArray
        outputStream.close()
        ByteString.fromArray(bytes)
      }

      new Writeable[MultipartFormData[TemporaryFile]](
        transform,
        entity.map(_.getContentType.getValue)
      )
    }
  }

  implicit def writableOfAnyContentAsMultiPartFormData(
    implicit
    codec: Codec
  ): Writeable[AnyContentAsMultipartFormData] = {
    MultipartWriteable.writableOfMultiPartFormData(codec).map { c =>
      c.asMultipartFormData.getOrElse(MultipartFormData(Map(), List(), List()))
    }
  }

}
