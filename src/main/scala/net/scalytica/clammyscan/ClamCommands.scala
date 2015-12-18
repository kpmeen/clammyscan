package net.scalytica.clammyscan

object ClamCommands {

  val instream = "zINSTREAM\u0000".getBytes
  val ping = "zPING\u0000".getBytes
  val status = "nSTATS\n".getBytes

  val okResponse = "stream: OK"
  val maxSizeExceededResponse = "INSTREAM size limit exceeded. ERROR"

}