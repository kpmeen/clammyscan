package net.scalytica.clammyscan


abstract class ClamError {
  val message: String
  val isVirus: Boolean
}

case class VirusFound(message: String, isVirus: Boolean = true) extends ClamError

case class ScanError(message: String, isVirus: Boolean = false) extends ClamError

case class DuplicateFile(message: String, isVirus: Boolean = false) extends ClamError

case class InvalidFilename(message: String, isVirus: Boolean = false) extends ClamError

case class FileOk()