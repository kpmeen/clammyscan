package net.scalytica.clammyscan

abstract class ClamError {
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

// Some general exceptions to allow for failing early and abort the file as soon as possible...
case class InvalidFilenameException(message: String) extends Exception(message)

case class DuplicateFileException(message: String) extends Exception(message)
