package net.scalytica.clammyscan

sealed trait ScanResponse

sealed abstract class ClamError extends ScanResponse {
  val message: String
  val isVirus: Boolean
}

case class VirusFound(message: String) extends ClamError {
  val isVirus = true
}

case class ScanError(message: String) extends ClamError {
  val isVirus = false
}

case class InvalidFilename(message: String) extends ClamError {
  val isVirus = false
}

case object FileOk extends ScanResponse

case class ScannedBody[A](scanResponse: ScanResponse, maybeRef: Option[A])