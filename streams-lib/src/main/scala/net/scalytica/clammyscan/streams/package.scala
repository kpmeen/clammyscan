package net.scalytica.clammyscan

import akka.stream.scaladsl.Sink
import akka.util.ByteString

import scala.concurrent.Future

// $COVERAGE-OFF$
package object streams {
  type ClamSink      = Sink[ByteString, Future[ScanResponse]]
  type SaveSink[A]   = Sink[ByteString, Future[Option[A]]]
  type ToSaveSink[A] = (String, Option[String]) => SaveSink[A]

  val ConnectionError = (filename: String) =>
    s"Failed to scan $filename with clamd because of a connection error. " +
      "Most likely because size limit was exceeded."

  val UnknownError = (filename: String) =>
    s"An unknown error occurred while trying to scan $filename with clamd"

  val CouldNotConnect = ScanError("Connection to clamd caused an exception.")

  val FileEmptyOrMissing = ScanError("File is either empty or missing.")

  val CannotScanEmptyFile = ScanError("Cannot scan an empty file")
}

// $COVERAGE-ON$
