package net.scalytica.clammyscan

import akka.util.ByteString

object ClamProtocol {

  // ----------
  // Some String based constants with values of different response messages
  // that can be expected from clamd.
  // ----------
  val unknownCommand = "UNKNOWN COMMAND"
  val okResponse = "stream: OK"
  val maxSizeExceededResponse = "INSTREAM size limit exceeded. ERROR"

  // The unicode null character...must be appended to some clam commands
  val unicodeNull = "\u0000"

  /**
   * Definition commands that can be used against clamd.
   */
  sealed trait Command {
    val str: String
    lazy val cmd: ByteString = ByteString.fromString(str)
  }

  object Command {

    def byteStringToCmd(bs: ByteString): Option[Command] = {
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

  /**
   * Command for initializing stream based AV scanning
   */
  case object Instream extends Command {
    val str = s"zINSTREAM$unicodeNull"
  }

  /**
   * Command for sending clamd a ping
   */
  case object Ping extends Command {
    val str = s"zPING$unicodeNull"
  }

  /**
   * Command for retrieving a status message from clamd
   */
  case object Status extends Command {
    val str = s"zSTATS$unicodeNull"
  }

  /**
   * Command for retrieving the version of the clamd
   */
  case object Version extends Command {
    val str = "VERSION"
  }

}