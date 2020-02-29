package net.scalytica.test

import net.scalytica.clammyscan._
import net.scalytica.clammyscan.streams.{
  ClamError,
  FileOk,
  ScannedBody,
  VirusFound
}
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc
import play.api.mvc.Results.{BadRequest, ExpectationFailed, NotAcceptable, Ok}
import play.api.mvc._

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

trait TestActions {

  type TestActionBuilder = ActionBuilder[Request, AnyContent]

  def multipartScanner[A](
      scan: ClamParser[A]
  )(
      f: ScannedBody[A] => Result
  )(implicit ec: ExecutionContext): Action[ClamMultipart[A]] =
    new ActionBuilderImpl(scan).apply { req =>
      req.body.files.headOption
        .map(fp => f(fp.ref))
        .getOrElse(
          ExpectationFailed(Json.obj("message" -> "Multipart with no content"))
        )
    }

  def directScanner[A](
      scan: ChunkedClamParser[A]
  )(
      f: ScannedBody[A] => Result
  )(implicit ec: ExecutionContext): Action[ScannedBody[A]] =
    new ActionBuilderImpl(scan).apply(r => f(r.body))

  private[this] def scanOnlyResHandler(sb: ScannedBody[Unit]): Result = {
    sb.scanResponse match {
      case FileOk =>
        sb.maybeRef
          .map { _ =>
            ExpectationFailed(
              Json.obj("message" -> "File should not be persisted")
            )
          }
          .getOrElse(Ok)

      case vf: VirusFound =>
        NotAcceptable(Json.obj("message" -> vf.message))

      case ce: ClamError =>
        BadRequest(Json.obj("message" -> ce.message))
    }
  }

  private[this] def scanTmpResHandler[A: ClassTag](
      parser: ClammyScanParser,
      sb: ScannedBody[A]
  ): Result = {
    sb.scanResponse match {
      case FileOk =>
        sb.maybeRef.map(_ => Ok).getOrElse {
          ExpectationFailed(
            Json.obj("message" -> "File should be persisted")
          )
        }

      case vf: VirusFound =>
        if (parser.clamConfig.canRemoveInfectedFiles) {
          sb.maybeRef
            .map { _ =>
              ExpectationFailed(
                Json.obj("message" -> "File should not be persisted")
              )
            }
            .getOrElse {
              NotAcceptable(Json.obj("message" -> vf.message))
            }
        } else {
          sb.maybeRef
            .map { _ =>
              NotAcceptable(Json.obj("message" -> vf.message))
            }
            .getOrElse {
              ExpectationFailed(
                Json.obj("message" -> "File should be persisted")
              )
            }
        }

      case ce: ClamError =>
        BadRequest(Json.obj("message" -> ce.message))
    }
  }

  def multipartScanOnlyAction(
      parser: ClammyScanParser
  )(implicit ec: ExecutionContext): EssentialAction = {
    multipartScanner[Unit](parser.scanOnly)(scanOnlyResHandler)
  }

  def directScanOnlyAction(
      parser: ClammyScanParser
  )(implicit ec: ExecutionContext): EssentialAction = {
    directScanner[Unit](parser.directScanOnly)(scanOnlyResHandler)
  }

  def multipartScanTmpAction(
      parser: ClammyScanParser
  )(implicit ec: ExecutionContext): EssentialAction = {
    multipartScanner[TemporaryFile](parser.scanWithTmpFile) { sb =>
      scanTmpResHandler(parser, sb)
    }
  }

  def directTmpAction(
      parser: ClammyScanParser
  )(implicit ec: ExecutionContext): EssentialAction = {
    directScanner[TemporaryFile](parser.directScanWithTmpFile) { sb =>
      scanTmpResHandler(parser, sb)
    }
  }

  def pingAction(
      parser: ClammyScanParser
  )(implicit ec: ExecutionContext): EssentialAction = {
    new mvc.ActionBuilder.IgnoringBody().async { _ =>
      parser.ping.map { pr =>
        Ok(Json.obj("ping" -> pr.trim))
      }
    }
  }

  def versionAction(
      parser: ClammyScanParser
  )(implicit ec: ExecutionContext): EssentialAction = {
    new mvc.ActionBuilder.IgnoringBody().async { _ =>
      parser.version.map(vr => Ok(Json.obj("version" -> vr.trim)))
    }
  }

  def statsAction(
      parser: ClammyScanParser
  )(implicit ec: ExecutionContext): EssentialAction = {
    new mvc.ActionBuilder.IgnoringBody().async { _ =>
      parser.version.map(sr => Ok(Json.obj("stats" -> sr.trim)))
    }
  }

}
