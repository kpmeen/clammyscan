package net.scalytica.clammyscan

import java.io.DataOutputStream
import java.net.{InetSocketAddress, Socket}

import play.api.Logger
import play.api.libs.iteratee.{Enumerator, Iteratee}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class ClamSocket(host: String, port: Int, timeout: Int) extends ClamCommands {

  val logger = Logger(this.getClass)

  val socket = connect()
  val out = new DataOutputStream(socket.getOutputStream)
  val in = socket.getInputStream

  start()

  /**
   * Configures and initialises a new TCP Socket connection to clamd...
   * @return a new and connected Socket to clamd
   */
  private def connect(): Socket = {
    logger.info(s"Using config values: host=$host, port=$port, timeout=$timeout")
    try {
      val theSocket = new Socket
      theSocket.setSoTimeout(timeout)
      theSocket.connect(new InetSocketAddress(host, port))
      theSocket
    } catch {
      case e: Throwable =>
        logger.error("Could not connect to clamd.", e)
        throw e
    }
  }

  private def start() {
    // Send the INSTREAM command to clamd...which indicates it should expect a new input stream
    out.write(instream)
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
   * Try to get the scan response from clamd...
   */
  def clamResponse: String = {
    out.writeInt(0)
    out.flush()

    // Consume the response stream from clamav using an enumerator...
    val virusInformation = Await.result(Enumerator.fromStream(in) run Iteratee.fold[Array[Byte], String]("") {
      case (s: String, bytes: Array[Byte]) =>
        s"$s${new String(bytes)}"
    }, Duration.Inf)

    logger.debug("Response from clamd: " + virusInformation)
    virusInformation.trim
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

object ClamSocket extends ClamConfig {
  def apply() = new ClamSocket(host, port, timeout)
}