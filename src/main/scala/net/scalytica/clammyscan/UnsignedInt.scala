package net.scalytica.clammyscan

import akka.util.ByteString

object UnsignedInt {

  private[clammyscan] val StreamCompleted = unsignedInt(0)

  /**
   * Helper function to convert an Int value to a ByteString representing
   * a 4 byte unsigned integer in network byte order.
   *
   * @param v The integer value to convert
   * @return ByteString of 4 byte unsigned integer in network byte order.
   */
  private[clammyscan] def unsignedInt(v: Int) = ByteString.fromArray(
    Array[Byte](
      ((v >>> 24) & 0xFF).toByte,
      ((v >>> 16) & 0xFF).toByte,
      ((v >>> 8) & 0xFF).toByte,
      ((v >>> 0) & 0xFF).toByte
    )
  )

}
