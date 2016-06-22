package net.scalytica.clammyscan

import akka.util.ByteString
import net.scalytica.clammyscan.ClamProtocol.{ByteStrCommand, Instream}
import org.scalatest.{Matchers, WordSpec}

class ClamProtocolSpec extends WordSpec with Matchers {

  "A ByteString" should {
    "match a ByteStrCmd" in {
      val istream = ByteString.fromString(TestHelper.instream)

      ByteStrCommand.isCommand(istream) shouldBe true
    }
    "not match a ByteStrCmd" in {
      val foo = ByteString.fromString("Foo")

      ByteStrCommand.isCommand(foo) shouldBe false
    }
  }

}
