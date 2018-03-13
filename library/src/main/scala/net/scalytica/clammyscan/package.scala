package net.scalytica

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import play.api.mvc.{BodyParser, MultipartFormData}

import scala.concurrent.Future

// $COVERAGE-OFF$No need for coverage here...
package object clammyscan {

  type ClamMultipart[A] = MultipartFormData[ScannedBody[A]]

  type ClamSink    = Sink[ByteString, Future[ScanResponse]]
  type SaveSink[A] = Sink[ByteString, Future[Option[A]]]

  type ToSaveSink[A] = (String, Option[String]) => SaveSink[A]

  type ClamParser[A] = BodyParser[ClamMultipart[A]]

  type ChunkedClamParser[A] = BodyParser[ScannedBody[A]]

  private[clammyscan] val ConnectionError = (filename: String) =>
    s"Failed to scan $filename with clamd because of a connection error. " +
    "Most likely because size limit was exceeded."

  private[clammyscan] val UnhandledException =
    "An unhandled exception was caught"

  private[clammyscan] val UnknownError = (filename: String) =>
    s"An unknown error occured while trying to scan $filename with clamd"

  private[clammyscan] val CouldNotConnect =
    ScanError("Connection to clamd caused an exception.")

  private[clammyscan] val CannotScanEmptyFile =
    ScanError("Cannot scan an empty file")
}
// $COVERAGE-ON$
