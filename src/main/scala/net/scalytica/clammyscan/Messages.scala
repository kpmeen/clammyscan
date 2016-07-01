package net.scalytica.clammyscan

sealed abstract class ClamError {
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

case class FileOk()
