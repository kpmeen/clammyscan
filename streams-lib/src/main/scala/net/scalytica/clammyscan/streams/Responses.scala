package net.scalytica.clammyscan.streams

import net.scalytica.clammyscan.streams.ClamProtocol._

sealed trait ScanResponse

object ScanResponse {

  private[this] val MaxSizeExceededError = ScanError(
    s"Scan failed because: $MaxSizeExceededResponse"
  )

  def fromString(s: String): ScanResponse = {
    if (OkResponse.equals(s)) FileOk
    else if (UnknownCommand.equals(s)) ScanError(s)
    else if (s.startsWith(MaxSizeExceededResponse)) MaxSizeExceededError
    else if (InfectedResponse.findFirstIn(s).nonEmpty) VirusFound(s)
    else ScanError(IncompleteResponse)
  }

}

sealed abstract class ClamError extends ScanResponse {
  val message: String
  val isVirus: Boolean
}

case class VirusFound(message: String) extends ClamError {
  override val isVirus = true
}

case class ScanError(message: String) extends ClamError {
  override val isVirus = false
}

case class InvalidFilename(message: String) extends ClamError {
  override val isVirus = false
}

case object FileOk extends ScanResponse

case class ScannedBody[A](scanResponse: ScanResponse, maybeRef: Option[A])
