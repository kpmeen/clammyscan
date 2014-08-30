package net.scalytica.clammyscan


trait ClamCommands {

  val instream = "zINSTREAM\0".getBytes
  val ping = "zPING\0".getBytes
  val status = "nSTATS\n".getBytes

  val okResponse = "stream: OK"
  val maxSizeExceededResponse = "INSTREAM size limit exceeded. ERROR"

}