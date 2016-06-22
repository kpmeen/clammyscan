package net.scalytica

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import play.api.mvc.{BodyParser, MultipartFormData}

import scala.concurrent.Future

package object clammyscan {

  type ClamResponse = Either[ClamError, FileOk]
  type TupledResponse[A] = (ClamResponse, Option[A])
  type ClamSink = Sink[ByteString, Future[ClamResponse]]
  type SaveSink[A] = Sink[ByteString, Option[A]]
  type ClamParser[A] = BodyParser[MultipartFormData[TupledResponse[A]]]

  private[clammyscan] val connectionError = (filename: String) =>
    s"Failed to scan $filename with clamd because of a connection error. " +
      "Most likely because size limit was exceeded."

  private[clammyscan] val unhandledException =
    "An unhandled exception was caught"

  private[clammyscan] val unknownError = (filename: String) =>
    s"An unknown error occured while trying to " +
      s"scan $filename with clamd"

  private[clammyscan] val couldNotConnect =
    ScanError("Could not connect to clamd")
}
