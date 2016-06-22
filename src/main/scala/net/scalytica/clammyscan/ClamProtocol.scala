package net.scalytica.clammyscan

import akka.util.ByteString

object ClamProtocol {

  val unknownCommand = "UNKNOWN COMMAND"
  val okResponse = "stream: OK"
  val maxSizeExceededResponse = "INSTREAM size limit exceeded. ERROR"

  sealed trait ByteStrCommand {
    val str: String
    lazy val cmd: ByteString = ByteString.fromString(str)
  }

  object ByteStrCommand {

    def byteStringToCmd(bs: ByteString): Option[ByteStrCommand] = {
      bs.utf8String match {
        case Instream.str => Some(Instream)
        case Ping.str => Some(Ping)
        case Status.str => Some(Status)
        case Version.str => Some(Version)
        case _ => None
      }
    }

    def isCommand(bs: ByteString): Boolean = byteStringToCmd(bs).nonEmpty

  }

  case object Instream extends ByteStrCommand {
    val str = "zINSTREAM\u0000"
  }

  case object Ping extends ByteStrCommand {
    val str = "zPING\u0000"
  }

  case object Status extends ByteStrCommand {
    val str = "zSTATS\u0000"
  }

  case object Version extends ByteStrCommand {
    val str = "VERSION"
  }

}