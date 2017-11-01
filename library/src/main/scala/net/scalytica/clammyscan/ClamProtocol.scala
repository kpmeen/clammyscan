package net.scalytica.clammyscan

import akka.util.ByteString

object ClamProtocol {

  // ----------
  // Some String based constants with values of different response messages
  // that can be expected from clamd.
  // ----------
  val UnknownCommand          = "UNKNOWN COMMAND"
  val OkResponse              = "stream: OK"
  val MaxSizeExceededResponse = "INSTREAM size limit exceeded"

  // The unicode null character...must be appended to some clam commands
  val UnicodeNull = "\u0000"

  /**
   * Definition commands that can be used against clamd.
   */
  sealed trait Command {
    val cmd: ByteString
  }

  object Command {

    def byteStringToCmd(bs: ByteString): Option[Command] = {
      bs match {
        case Instream.cmd => Some(Instream)
        case Ping.cmd     => Some(Ping)
        case Stats.cmd    => Some(Stats)
        case Version.cmd  => Some(Version)
        case _            => None
      }
    }

    def isCommand(bs: ByteString): Boolean = byteStringToCmd(bs).nonEmpty

  }

  /**
   * Command for initializing stream based AV scanning
   */
  case object Instream extends Command {
    val cmd = ByteString.fromString(s"zINSTREAM$UnicodeNull")
  }

  /**
   * Command for sending clamd a ping
   */
  case object Ping extends Command {
    val cmd = ByteString.fromString(s"zPING$UnicodeNull")
  }

  /**
   * Command for retrieving a statistics message from clamd
   */
  case object Stats extends Command {
    val cmd = ByteString.fromString(s"zSTATS$UnicodeNull")
  }

  /**
   * Command for retrieving the version of the clamd
   */
  case object Version extends Command {
    val cmd = ByteString.fromString("VERSION")
  }

}
