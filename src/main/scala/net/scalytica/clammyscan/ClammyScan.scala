package net.scalytica.clammyscan

import java.io._
import java.net.{InetSocketAddress, Socket, SocketException}
import java.util

import play.api.{Play, Logger}
import play.api.libs.iteratee._

import scala.concurrent._
import scala.concurrent.duration._


trait ClamConfig {
  /*
  * IP address of clamd daemon. Defaults to localhost ("clamserver" if no play application is available)
  */
  val host = Play.maybeApplication.fold("clamserver")(_.configuration.getString("clammyscan.clamd.host").getOrElse("localhost"))

  /**
   * port of clamd daemon. Defaults to 3310
   */
  val port = Play.maybeApplication.fold(3310)(_.configuration.getInt("clammyscan.clamd.port").getOrElse(3310))

  /**
   * Socket timeout for clam. Defaults to 5 seconds when running in a play application (otherwise default is 0).
   */
  val timeout = Play.maybeApplication.fold(0)(_.configuration.getInt("clammyscan.clamd.port").getOrElse(5000))

  /**
   * Clam socket commands
   */
  val instream = "zINSTREAM\0".getBytes
  val ping = "zPING\0".getBytes
  val status = "nSTATS\n".getBytes
  // OK response from clam
  val okResponse = "stream: OK"
  val maxSizeExceededResponse = "INSTREAM size limit exceeded. ERROR"
}

// ---------------------------------------------------------------------------------------------------------

abstract class ClamError {
  val message: String
  val isVirus: Boolean
}

case class VirusFound(message: String, isVirus: Boolean = true) extends ClamError

case class ScanError(message: String, isVirus: Boolean = false) extends ClamError

case class FileOk()

// ---------------------------------------------------------------------------------------------------------

/**
 * Allows scanning file streams for viruses using a clamd over TCP.
 */
class ClammyScan extends ClamConfig {

  val logger = Logger(this.getClass)

  private def connectionError(filename: String) = s"Failed to scan $filename with clamd because of a connection error. Most likely because size limit was exceeded."
  private def unknownError(filename: String) = s"An unexpected exception was caught while trying to scan $filename with clamd"

  logger.debug(s"Using config values: host=$host, port=$port, timeout=$timeout")

  /**
   * Iteratee based on the reactive mongo GridFS iteratee... adapted for clammy pleasures
   */
  def clamScan(filename: String, chunkSize: Int = 262144)(implicit ec: ExecutionContext): Iteratee[Array[Byte], Either[ClamError, FileOk]] = {
    logger.info(s"Preparing to scan file $filename with clamd...")

    val socket = configureSocket()
    val out = new DataOutputStream(socket.getOutputStream)
    val in = socket.getInputStream

    // Send the INSTREAM command to clamd...which indicates it should expect a new input stream
    out.write(instream)

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
          writeChunk(n + ci._2, ci._1)
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

        writeChunk(n, previous)
        out.writeInt(0)
        out.flush()

        val res = responseFromClamd()
        if (okResponse.equals(res.trim)) {
          logger.info(s"No viruses found in $filename")
          terminate()
          Right(FileOk())
        } else {
          logger.warn(s"Virus detected in $filename: $res")
          terminate()
          Left(VirusFound(res))
        }
      }

      /**
       * Try to get the scan response from clamd...
       */
      private def responseFromClamd() = {
        // Consume the response stream from clamav using an enumerator...
        val virusInformation = Await.result(Enumerator.fromStream(in) run Iteratee.fold[Array[Byte], String]("") {
          case (s: String, bytes: Array[Byte]) =>
            s"$s${new String(bytes)}"
        }, Duration.Inf)

        logger.debug("Response from clamd: " + virusInformation)
        virusInformation.trim
      }

      /**
       * Write a chunk to the clamd socket...
       */
      def writeChunk(n: Int, array: Array[Byte]) {
        logger.debug("writing chunk " + n)
        out.writeInt(array.length)
        out.write(array)
        out.flush()
      }

      /**
       * Close the TCP socket connection to clamd
       */
      def terminate() {
        socket.close()
        out.close()
        logger.info("TCP socket to clamd is now closed")
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
   * Configures and initialises a new TCP Socket connection to clamd...
   * @return a new and connected Socket to clamd
   */
  def configureSocket() = {
    val theSocket = new Socket
    theSocket.setSoTimeout(timeout)
    theSocket.connect(new InetSocketAddress(host, port))
    theSocket
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