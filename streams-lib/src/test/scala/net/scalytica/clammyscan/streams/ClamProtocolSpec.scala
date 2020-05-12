package net.scalytica.clammyscan.streams

import akka.util.ByteString
import net.scalytica.clammyscan.streams.ClamProtocol.Command
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class ClamProtocolSpec extends AnyWordSpecLike with Matchers {

  "A ByteString" should {
    "match an Instream command" in {
      val cmd = ByteString.fromString(TestHelpers.instreamCmd)
      Command.isCommand(cmd) shouldBe true
    }
    "match a Ping command" in {
      val cmd = ByteString.fromString(TestHelpers.pingCmd)
      Command.isCommand(cmd) shouldBe true
    }
    "match a Status command" in {
      val cmd = ByteString.fromString(TestHelpers.statusCmd)
      Command.isCommand(cmd) shouldBe true
    }
    "match a Version command" in {
      val cmd = ByteString.fromString(TestHelpers.versionCmd)
      Command.isCommand(cmd) shouldBe true
    }
    "not match any Command" in {
      val foo = ByteString.fromString("Foo")
      Command.isCommand(foo) shouldBe false
    }
  }

}
