package net.scalytica.clammyscan

import java.net.SocketException
import java.util

import play.api.Logger
import play.api.libs.iteratee._

import scala.concurrent._


/**
 * Allows scanning file streams for viruses using a clamd over TCP.
 */
class ClammyScan(clamSocket: ClamSocket) extends ClamCommands {

  val logger = Logger(this.getClass)

  private def connectionError(filename: String) = s"Failed to scan $filename with clamd because of a connection error. Most likely because size limit was exceeded."

  private def unknownError(filename: String) = s"An unexpected exception was caught while trying to scan $filename with clamd"

  /**
   * Iteratee based on the reactive mongo GridFS iteratee... adapted for clammy pleasures
   */
  def clamScan(filename: String, chunkSize: Int = 262144)(implicit ec: ExecutionContext): Iteratee[Array[Byte], Either[ClamError, FileOk]] = {
    logger.info(s"Preparing to scan file $filename with clamd...")

    /**
     * local case class for handling chunks being sent to the Iteratee
     *
     * @param previous the previous chunk
     * @param n the chunk number
     * @param length the lenght of stream that has been processed
     */
    case class ClamChunk(previous: Array[Byte] = new Array(0), n: Int = 0, length: Int = 0) {

      /**
       * Feeder function for processing chunks of bytes...
       */
      def feed(chunk: Array[Byte]): ClamChunk = {
        val wholeChunk = concat(previous, chunk)
        val normalizedChunkNumber = wholeChunk.length / chunkSize

        logger.debug("wholeChunk size is " + wholeChunk.length + " => " + normalizedChunkNumber)

        val zipped = for (i <- 0 until normalizedChunkNumber) yield util.Arrays.copyOfRange(wholeChunk, i * chunkSize, (i + 1) * chunkSize) -> i
        val left = util.Arrays.copyOfRange(wholeChunk, normalizedChunkNumber * chunkSize, wholeChunk.length)

        zipped.foreach { ci =>
          clamSocket.writeChunk(n + ci._2, ci._1)
        }

        ClamChunk(
          if (left.isEmpty) Array.empty else left,
          n + normalizedChunkNumber,
          length + chunk.length
        )
      }

      /**
       * Process the last chunk and return the end result from clamd...
       */
      def finish: Either[ClamError, FileOk] = {
        logger.debug("writing last chunk (n=" + n + ")!")

        clamSocket.writeChunk(n, previous)
        val res = clamSocket.clamResponse
        if (okResponse.equals(res.trim)) {
          logger.info(s"No viruses found in $filename")
          clamSocket.terminate()
          Right(FileOk())
        } else {
          logger.warn(s"Virus detected in $filename: $res")
          clamSocket.terminate()
          Left(VirusFound(res))
        }
      }

    }

    // start sending chunks to clamav...
    Iteratee.fold(ClamChunk()) { (previous, chunk: Array[Byte]) =>
      logger.debug("processing new enumerated chunk from n=" + previous.n + "...\n")
      previous.feed(chunk)
    }.map(cc => {
      logger.debug(s"Processing last chunk...")
      cc.finish
    }).recover {
      case se: SocketException =>
        logger.warn(connectionError(filename))
        Left(ScanError(connectionError(filename)))
      case e: Exception =>
        logger.error(unknownError(filename), e)
        Left(ScanError(unknownError(filename)))
    }
  }

  /**
   * Concatenate two arrays with each other...
   */
  private def concat[T](a1: Array[T], a2: Array[T])(implicit m: Manifest[T]): Array[T] = {
    var i, j = 0
    val result = new Array[T](a1.length + a2.length)
    while (i < a1.length) {
      result(i) = a1(i)
      i = i + 1
    }
    while (j < a2.length) {
      result(i + j) = a2(j)
      j = j + 1
    }
    result
  }

}

abstract class ClamError {
  val message: String
  val isVirus: Boolean
}

case class VirusFound(message: String, isVirus: Boolean = true) extends ClamError

case class ScanError(message: String, isVirus: Boolean = false) extends ClamError

case class FileOk()