package net.scalytica.clammyscan

import java.io.File

import akka.actor.ActorSystem
import akka.stream.Materializer
import net.scalytica.clammyscan.MultipartFormDataWriteable.acAsMultiPartWritable
import net.scalytica.clammyscan.TestHelpers._
import org.scalatestplus.play._
import play.api.Configuration
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.api.test.Helpers._
import play.api.test._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ClammyScanSpec extends PlaySpec with OneAppPerSuite {

  implicit val sys: ActorSystem = app.actorSystem
  implicit val mat: Materializer = app.materializer
  implicit val cfg: Configuration = app.configuration

  val clammyScan = new ClammyScanParser(sys, mat, cfg)

  def scanner[A](scan: ClamParser[A])(right: Option[A] => Result) = // scalastyle:ignore
    Action(scan) { req =>
      req.body.files.headOption.map { fp =>
        fp.ref._1 match {
          case Right(ok) =>
            right(fp.ref._2)
          case Left(err) =>
            err match {
              case vf: VirusFound =>
                if (fp.ref._2.nonEmpty)
                  Results.ExpectationFailed("File present with virus")
                else
                  Results.NotAcceptable(err.message)
              case ce =>
                Results.BadRequest
            }
        }
      }.getOrElse(Results.ExpectationFailed("Multipart with no content"))
    }

  val scanOnlyAction: EssentialAction =
    scanner[Unit](clammyScan.scanOnly) { right =>
      if (right.isEmpty) Results.Ok
      else Results.ExpectationFailed("File should not be persisted")
    }

  val scanTmpFile: EssentialAction =
    scanner[TemporaryFile](clammyScan.scanWithTmpFile) { right =>
      right.map { f =>
        Results.Ok(s"filename: ${f.file.getName}")
      }.getOrElse(Results.InternalServerError("No file attached"))
    }

  private def fakeReq(file: File, ctype: Option[String]) = {
    val mfd = MultipartFormData(
      dataParts = Map.empty,
      files = Seq(FilePart(
        key = "file",
        filename = file.getName,
        contentType = ctype,
        ref = TemporaryFile(file)
      )),
      badParts = Seq.empty
    )
    FakeRequest(Helpers.POST, "/").withMultipartFormDataBody(mfd)
  }

  private def awaitResult(
    action: EssentialAction,
    req: FakeRequest[AnyContentAsMultipartFormData]
  ): Result =
    await(Helpers.call(action, req))

  "A ClammyScan with default configuration" which {

    "receives a file for scanning only" should {
      "scan infected file and not persist the file" in {
        val request = fakeReq(eicarZipFile, Some("application/zip"))
        val result = awaitResult(scanOnlyAction, request)

        result.header.status mustEqual NOT_ACCEPTABLE
      }

      "scan clean file and not persist the file" in {
        val request = fakeReq(cleanFile, Some("application/pdf"))
        val result = awaitResult(scanOnlyAction, request)

        result.header.status mustEqual OK
      }
    }

    "receives a file for scanning and saving as temp file" should {
      "scan infected file and remove the temp file" in {
        val request = fakeReq(eicarFile, None)
        val result = awaitResult(scanTmpFile, request)

        result.header.status mustEqual NOT_ACCEPTABLE
      }
      "scan clean file and not remove the temp file" in {
        val request = fakeReq(cleanFile, Some("application/pdf"))
        val result = awaitResult(scanTmpFile, request)

        result.header.status mustEqual OK
        val body = Await.result(
          result.body.consumeData.map[String](_.utf8String), 10 seconds
        )
        body must startWith("filename: multipartBody")
        body must endWith("scanWithTempFile")
      }
    }
  }

  // TODO: Tests with other configuration settings

}
