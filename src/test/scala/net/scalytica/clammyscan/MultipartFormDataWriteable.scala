package net.scalytica.clammyscan

import java.nio.file.{Files, Paths}

import play.api.http.{HeaderNames, Writeable}
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{AnyContentAsMultipartFormData, Codec, MultipartFormData}

object MultipartFormDataWriteable {
  val boundary = "--------ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"

  private[this] def formatDataParts(data: Map[String, Seq[String]]) = {
    val dataParts = data.flatMap {
      case (key, values) =>
        values.map { v =>
          val name = s""""$key""""
          s"--$boundary\r\n${HeaderNames.CONTENT_DISPOSITION}: " +
            s"form-data; name=$name\r\n\r\n$v\r\n"
        }
    }.mkString("")

    Codec.utf_8.encode(dataParts)
  }

  private[this] def filePartHeader(file: FilePart[TemporaryFile]) = {
    val name     = s""""${file.key}""""
    val filename = s""""${file.filename}""""
    val contentType = file.contentType.map { ct =>
      s"${HeaderNames.CONTENT_TYPE}: $ct\r\n"
    }.getOrElse("")

    Codec.utf_8.encode(
      s"--$boundary\r\n${HeaderNames.CONTENT_DISPOSITION}: " +
        s"form-data; name=$name; filename=$filename\r\n$contentType\r\n"
    )
  }

  val singleton = Writeable[MultipartFormData[TemporaryFile]](
    transform = { form: MultipartFormData[TemporaryFile] =>
      val formDataParts = formatDataParts(form.dataParts)
      val fileParts = form.files.flatMap { f =>
        val path  = f.ref.file.getAbsolutePath
        val bytes = Files.readAllBytes(Paths.get(path))
        filePartHeader(f) ++ bytes ++ Codec.utf_8.encode("\r\n")
      }

      formDataParts ++ fileParts ++ Codec.utf_8.encode(s"--$boundary--")
    },
    contentType = Some(s"multipart/form-data; boundary=$boundary")
  )

  implicit val acAsMultiPartWritable
    : Writeable[AnyContentAsMultipartFormData] =
    singleton.map(_.mdf)
}
