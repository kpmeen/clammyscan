package net.scalytica.test

import akka.NotUsed
import akka.stream.scaladsl.Source
import akka.stream.{IOResult, Materializer}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import net.scalytica.clammyscan.FileSource
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.Files.TemporaryFileCreator
import play.api.libs.ws.{BodyWritable, WSClient, WSResponse}
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.Result
import play.api.test.Writeables
import play.api.{Application, Configuration}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.reflect.ClassTag

// scalastyle:off line.size.limit magic.number
trait TestAppContext
    extends PlaySpec
    with GuiceOneServerPerSuite
    with TestActions
    with ScalaFutures
    with Writeables {

  lazy val additionalConfig: Map[String, AnyRef] = Map.empty

  lazy val configuration: Configuration =
    Configuration(ConfigFactory.load()) ++ Configuration(
      "play.http.router"             -> "net.scalytica.test.TestRouter",
      "play.http.errorHandler"       -> "net.scalytica.test.TestErrorHandler",
      "akka.jvm-exit-on-fatal-error" -> "false",
      "akka.loglevel"                -> "DEBUG",
      "akka.loggers"                 -> Seq("akka.event.slf4j.Slf4jLogger"),
      "akka.logging-filter"          -> "akka.event.slf4j.Slf4jLoggingFilter"
    ) ++ Configuration(additionalConfig.toSeq: _*)

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = Span(50000L, Millis),
    interval = Span(50L, Millis)
  )

  override def fakeApplication(): Application = {
    new GuiceApplicationBuilder().configure(configuration).build
  }

  lazy val tmpFileCreator = app.injector.instanceOf[TemporaryFileCreator]

  implicit lazy val mat: Materializer  = app.materializer
  implicit val ec: ExecutionContext    = scala.concurrent.ExecutionContext.global
  implicit lazy val wsClient: WSClient = app.injector.instanceOf[WSClient]

  def multipart(
      fileSource: FileSource,
      contentType: Option[String] = None,
      altFilename: Option[String] = None
  ): Source[FilePart[Source[ByteString, Future[IOResult]]], NotUsed] = {
    val filePart = FilePart(
      key = "file",
      filename = altFilename.getOrElse(fileSource.fname),
      contentType = contentType,
      ref = fileSource.source
    )

    Source(filePart :: List())
  }

  def queryParams(
      fname: Option[String] = None,
      ctype: Option[String] = None
  ) = {
    val q = Map.newBuilder[String, String]
    fname.foreach(n => q += "filename"    -> n)
    ctype.foreach(c => q += "contentType" -> c)

    q.result().toSeq
  }

  /**
   * IMPORTANT: This function relies heavily on the validation done in the
   * `ClammyContext.scan*Action` helpers.
   */
  def validateResult(
      result: Result,
      expectedStatusCode: Int,
      expectedBody: Option[String] = None
  ): Unit = {
    result.header.status mustEqual expectedStatusCode
    expectedBody.foreach { eb =>
      val body = Await.result(
        result.body.consumeData.map[String](_.utf8String),
        20 seconds
      )
      body must include(eb)
    }
  }

  def post[A: ClassTag](
      uri: String,
      fname: Option[String] = None,
      ctype: Option[String] = None
  )(body: A)(implicit bw: BodyWritable[A]): Future[WSResponse] = {
    wsUrl(uri)
      .withQueryStringParameters(queryParams(fname, ctype): _*)
      .post(body)
  }
}

// scalastyle:on line.size.limit magic.number
