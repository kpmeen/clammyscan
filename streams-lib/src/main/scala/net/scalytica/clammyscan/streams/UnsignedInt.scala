package net.scalytica.clammyscan.streams

import akka.util.ByteString

object UnsignedInt {

  /**
   * Helper function to convert an Int value to a ByteString representing
   * a 4 byte unsigned integer in network byte order.
   *
   * @param v The integer value to convert
   * @return ByteString of 4 byte unsigned integer in network byte order.
   */
  private[clammyscan] def unsignedInt(v: Int) =
    ByteString.fromArray(
      Array[Byte](
        ((v >>> 24) & 0xff).toByte,
        ((v >>> 16) & 0xff).toByte,
        ((v >>> 8) & 0xff).toByte,
        ((v >>> 0) & 0xff).toByte
      )
    )
}
