package net.scalytica.clammyscan

import akka.util.ByteString
import net.scalytica.clammyscan.ClamProtocol.ByteStrCommand
import org.scalatest.{Matchers, WordSpec}

class ClamProtocolSpec extends WordSpec with Matchers {

  "A ByteString" should {
    "match a ByteStrCmd" in {
      val istream = ByteString.fromString(TestHelpers.instreamCmd)

      ByteStrCommand.isCommand(istream) shouldBe true
    }
    "not match a ByteStrCmd" in {
      val foo = ByteString.fromString("Foo")

      ByteStrCommand.isCommand(foo) shouldBe false
    }
  }

}