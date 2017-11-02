package net.scalytica.clammyscan

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import org.scalatest._
import play.api.Configuration
import play.api.http.Writeable
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.{TemporaryFile, TemporaryFileCreator}
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{PlayBodyParsers, _}
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.reflect.ClassTag

/**
 * Base spec to test the ClammyScan parsers through `EssentialAction`.
 */
trait ClammyTestContext extends WordSpecLike with MustMatchers {

  val baseExtraConfig: Map[String, Any] = Map(
    "akka.jvm-exit-on-fatal-error" -> false,
    "akka.loglevel"                -> "DEBUG",
    "akka.loggers"                 -> Seq("akka.event.slf4j.Slf4jLogger"),
    "logging-filter"               -> "akka.event.slf4j.Slf4jLoggingFilter"
  )

  case class Context(action: EssentialAction)(
      implicit mat: Materializer,
      tfc: TemporaryFileCreator
  ) {

    val materializer    = mat
    val tempFileCreator = tfc

    def awaitResult[A](
        req: FakeRequest[A]
    )(implicit wrt: Writeable[A]): Result = await(call(action, req))

  }

  type TestActionBuilder = ActionBuilder[Request, AnyContent]

  def withScanAction[T](
      e: ClammyScanParser => EssentialAction,
      additionalConfig: Map[String, Any] = Map.empty
  )(test: Context => T): T = {

    val config = Configuration(ConfigFactory.load()) ++ Configuration.from(
      additionalConfig ++ baseExtraConfig
    )

    val app = new GuiceApplicationBuilder().configure(config).build

    running(app) {
      val cfg        = app.configuration
      val sys        = app.actorSystem
      val mat        = app.materializer
      val tfc        = app.injector.instanceOf[TemporaryFileCreator]
      val pbp        = app.injector.instanceOf[PlayBodyParsers]
      val clammyScan = new ClammyScanParser(sys, mat, tfc, pbp, cfg)

      test(Context(e(clammyScan))(mat, tfc))
    }
  }

  def multipartScanner[A](
      scan: ClamParser[A]
  )(
      f: (ScannedBody[A]) => Result
  ): Action[ClamMultipart[A]] = new ActionBuilderImpl(scan).apply { req =>
    req.body.files.headOption
      .map(fp => f(fp.ref))
      .getOrElse(
        ExpectationFailed(Json.obj("message" -> "Multipart with no content"))
      )
  }

  def directScanner[A](
      scan: ChunkedClamParser[A]
  )(
      f: (ScannedBody[A]) => Result
  ): Action[ScannedBody[A]] = new ActionBuilderImpl(scan).apply(r => f(r.body))

  private[this] def scanOnlyResHandler(sb: ScannedBody[Unit]): Result = {
    sb.scanResponse match {
      case FileOk =>
        sb.maybeRef.map { _ =>
          ExpectationFailed(
            Json.obj("message" -> "File should not be persisted")
          )
        }.getOrElse(Ok)

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
          sb.maybeRef.map { _ =>
            ExpectationFailed(
              Json.obj("message" -> "File should not be persisted")
            )
          }.getOrElse {
            NotAcceptable(Json.obj("message" -> vf.message))
          }
        } else {
          sb.maybeRef.map { _ =>
            NotAcceptable(Json.obj("message" -> vf.message))
          }.getOrElse {
            ExpectationFailed(
              Json.obj("message" -> "File should be persisted")
            )
          }
        }

      case ce: ClamError =>
        BadRequest(Json.obj("message" -> ce.message))
    }
  }

  def scanOnlyAction(parser: ClammyScanParser): EssentialAction = {
    multipartScanner[Unit](parser.scanOnly)(scanOnlyResHandler)
  }

  def directScanOnlyAction(parser: ClammyScanParser): EssentialAction = {
    directScanner[Unit](parser.directScanOnly)(scanOnlyResHandler)
  }

  def scanTmpAction(parser: ClammyScanParser): EssentialAction = {
    multipartScanner[TemporaryFile](parser.scanWithTmpFile) { sb =>
      scanTmpResHandler(parser, sb)
    }
  }

  def directTmpAction(parser: ClammyScanParser): EssentialAction = {
    directScanner[TemporaryFile](parser.directScanWithTmpFile) { sb =>
      scanTmpResHandler(parser, sb)
    }
  }

  def fakeMultipartRequest(
      fileSource: FileSource,
      contentType: Option[String] = None,
      alternativeFilename: Option[String] = None
  )(implicit ctx: Context): FakeRequest[AnyContentAsMultipartFormData] = {
    implicit val mat: Materializer = ctx.materializer

    val tmpFile = ctx.tempFileCreator.create("test", s"${System.nanoTime}")
    val f = fileSource.source runWith FileIO
      .toPath(tmpFile.path)
      .mapMaterializedValue(_.map(_ => tmpFile))

    val mfd = MultipartFormData(
      dataParts = Map.empty,
      files = Seq(
        FilePart(
          key = "file",
          filename = alternativeFilename.getOrElse(fileSource.fname),
          contentType = contentType,
          ref = Await.result(f, 10 seconds)
        )
      ),
      badParts = Seq.empty
    )

    FakeRequest(POST, "/").withMultipartFormDataBody(mfd)
  }

  def fakeDirectRequest(
      fileSource: FileSource,
      contentType: Option[String] = None,
      alternativeFilename: Option[String] = None
  )(implicit ctx: Context): FakeRequest[AnyContentAsRaw] = {
    implicit val mat: Materializer = ctx.materializer

    val uri1 = alternativeFilename.map(f => s"/?filename=$f").getOrElse("/")
    val uri = contentType.map { ct =>
      if (uri1.contains("?")) s"$uri1&contentType=$ct"
      else s"$uri1?contentType=$ct"
    }.getOrElse(uri1)

    val body = Await.result(
      fileSource.source.runFold(ByteString.empty) { (bytes, chunk) =>
        bytes.concat(chunk)
      },
      2 seconds
    )

    FakeRequest(POST, uri).withRawBody(body)
  }

}
