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
  def clamScan(filename: String, chunkSize: Int = 262144)(implicit ec: ExecutionContext): Iteratee[Array[Byte], Future[Either[ClamError, FileOk]]] = {
    logger.info(s"Preparing to scan file $filename with clamd...")
    val startTime = System.currentTimeMillis()

    /*
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
      def finish: Future[Either[ClamError, FileOk]] = {
        logger.debug("writing last chunk (n=" + n + ")!")
        clamSocket.writeChunk(n, previous)

        logger.info(s"Waiting for scan report from clamav for $filename...")
        val waitStart = System.currentTimeMillis()

        clamSocket.clamResponse.flatMap[Either[ClamError, FileOk]](res => {
          val endTime = System.currentTimeMillis()
          logger.info(s"Scanning of $filename took ${endTime - startTime}ms. [Receiving: ${waitStart - startTime}ms - Processing: ${endTime - waitStart}ms]")

          if (okResponse.equals(res)) {
            logger.info(s"No viruses found in $filename")
            clamSocket.terminate()
            Future.successful(Right(FileOk()))
          } else {
            logger.warn(s"Virus detected in $filename: $res")
            clamSocket.terminate()
            Future.successful(Left(VirusFound(res)))
          }
        })
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
        Future.successful(Left(ScanError(connectionError(filename))))
      case e: Exception =>
        logger.error(unknownError(filename), e)
        Future.successful(Left(ScanError(unknownError(filename))))
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