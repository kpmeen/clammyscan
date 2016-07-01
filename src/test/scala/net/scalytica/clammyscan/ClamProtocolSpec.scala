package net.scalytica.clammyscan

import akka.util.ByteString
import net.scalytica.clammyscan.ClamProtocol.ByteStrCommand
import org.scalatest.{Matchers, WordSpec}

class ClamProtocolSpec extends WordSpec with Matchers {

  "A ByteString" should {
    "match an Instream command" in {
      val cmd = ByteString.fromString(TestHelpers.instreamCmd)
      ByteStrCommand.isCommand(cmd) shouldBe true
    }
    "match a Ping command" in {
      val cmd = ByteString.fromString(TestHelpers.pingCmd)
      ByteStrCommand.isCommand(cmd) shouldBe true
    }
    "match a Status command" in {
      val cmd = ByteString.fromString(TestHelpers.statusCmd)
      ByteStrCommand.isCommand(cmd) shouldBe true
    }
    "match a Version command" in {
      val cmd = ByteString.fromString(TestHelpers.versionCmd)
      ByteStrCommand.isCommand(cmd) shouldBe true
    }
    "not match any Command" in {
      val foo = ByteString.fromString("Foo")
      ByteStrCommand.isCommand(foo) shouldBe false
    }
  }

}
