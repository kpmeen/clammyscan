package net.scalytica.clammyscan

import play.api.Logger
import play.api.libs.iteratee._

import scala.concurrent._

/**
 * Allows scanning of file streams for viruses using a clamd over TCP.
 */
class ClammyScan(clamSocket: ClamSocket) extends ClamCommands {

  val logger = Logger(this.getClass)

  private val DefaultChunkSize = 262144

  private def connectionError(filename: String) =
    s"Failed to scan $filename with clamd because of a connection error. Most likely because size limit was exceeded."

  private def unknownError(filename: String) = s"An unexpected exception was caught while trying to scan $filename with clamd"

  /**
   * Iteratee that will send incoming chunks to ClamAV for scanning.
   */
  def clamScan(
    filename: String,
    chunkSize: Int = DefaultChunkSize
  )(implicit ec: ExecutionContext): Iteratee[Array[Byte], Future[Either[ClamError, FileOk]]] = {
    logger.info(s"Preparing to scan file $filename with clamd...")

    // start sending chunks to clamav...
    Iteratee.fold(ClamChunk(filename, chunkSize)) { (previous, chunk: Array[Byte]) =>
      logger.debug("processing new enumerated chunk from n=" + previous.n + "...\n")
      previous.feed(chunk)
    }.map(cc => {
      logger.debug(s"Processing last chunk...")
      cc.finish
    }).recover {
      case se: java.net.SocketException =>
        logger.warn(connectionError(filename))
        Future.successful(Left(ScanError(connectionError(filename))))
      case e: Exception =>
        logger.error(unknownError(filename), e)
        Future.successful(Left(ScanError(unknownError(filename))))
    }
  }

  /*
     * local case class for handling chunks being sent to the Iteratee
     *
     * @param filename the name of the file being processed
     * @param chunkSize the size of each chunk
     * @param previous the previous chunk
     * @param n the chunk number
     * @param length the length of stream that has been processed
     */
  private case class ClamChunk(
    filename: String,
    chunkSize: Int,
    previous: Array[Byte] = new Array(0),
    n: Int = 0,
    length: Int = 0
  ) {

    /**
     * Feeder function for processing chunks of bytes...
     */
    def feed(chunk: Array[Byte])(implicit ec: ExecutionContext): ClamChunk = {
      import java.util.Arrays.copyOfRange

      val wholeChunk = concat(previous, chunk)
      val normalizedChunkNumber = wholeChunk.length / chunkSize

      logger.debug("wholeChunk size is " + wholeChunk.length + " => " + normalizedChunkNumber)

      val zipped = for (i <- 0 until normalizedChunkNumber) yield copyOfRange(wholeChunk, i * chunkSize, (i + 1) * chunkSize) -> i
      val left = copyOfRange(wholeChunk, normalizedChunkNumber * chunkSize, wholeChunk.length)

      zipped.foreach(ci => clamSocket.writeChunk(n + ci._2, ci._1))

      copy(
        previous = if (left.isEmpty) Array.empty else left,
        n = n + normalizedChunkNumber,
        length = length + chunk.length
      )
    }

    /**
     * Process the last chunk and return the end result from clamd...
     */
    def finish(implicit ec: ExecutionContext): Future[Either[ClamError, FileOk]] = {
      logger.debug("writing last chunk (n=" + n + ")!")
      clamSocket.writeChunk(n, previous)

      logger.info(s"Waiting for scan report from clamav for $filename...")
      val waitStart = System.currentTimeMillis()

      clamSocket.clamResponse.flatMap[Either[ClamError, FileOk]](res => {
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