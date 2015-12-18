package net.scalytica.clammyscan

abstract class ClamError {
  val message: String
  val isVirus: Boolean
}

case class VirusFound(message: String, isVirus: Boolean = true) extends ClamError

case class ScanError(message: String, isVirus: Boolean = false) extends ClamError

case class FileOk()

// Some general exceptions to allow for failing early and abort the file as soon as possible...
case class InvalidFilenameException(message: String) extends Exception(message)

case class DuplicateFileException(message: String) extends Exception(message)
