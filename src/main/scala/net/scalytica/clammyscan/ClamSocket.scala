package net.scalytica.clammyscan

import java.io.DataOutputStream
import java.net.{InetSocketAddress, Socket}

import play.api.Logger
import play.api.libs.iteratee.{Enumerator, Iteratee}

import scala.util.Try

// TODO: Use a different execution context?
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClamSocket(host: String, port: Int, timeout: Int) {

  val logger = Logger(this.getClass)

  val socket: Option[Socket] = connect()

  private lazy val out = socket.map(s => new DataOutputStream(s.getOutputStream)).orNull
  private lazy val in = socket.map(s => s.getInputStream).orNull

  start()

  def isConnected: Boolean = socket.fold(false)(_.isConnected)

  /**
   * Configures and initialises a new TCP Socket connection to clamd...
   *
   * @return a new and connected Socket to clamd
   */
  private def connect(): Option[Socket] = {
    logger.info(s"Using config values: host=$host, port=$port, timeout=$timeout")
    (Try {
      val theSocket = new Socket
      theSocket.setSoTimeout(timeout)
      theSocket.connect(new InetSocketAddress(host, port))
      Some(theSocket)
    } recover {
      case e: Throwable =>
        logger.error("Could not connect to clamd!")
        None
    }).toOption.flatten
  }

  private def start() {
    if (isConnected) {
      // Send the INSTREAM command to clamd...which indicates it should expect a new input stream
      out.write(ClamCommands.instream)
    }
  }

  /**
   * Write a chunk to the clamd socket...
   */
  def writeChunk(n: Int, array: Array[Byte]) {
    if (isConnected) {
      logger.debug("writing chunk " + n)
      out.writeInt(array.length)
      out.write(array)
      out.flush()
    }
  }

  /**
   * Try to get the scan response from clamd...
   */
  def clamResponse: Future[String] = {
    if (isConnected) {
      out.writeInt(0)
      out.flush()

      // Consume the response stream from clamav using an enumerator...
      val virusInformation = Enumerator.fromStream(in) run Iteratee.fold[Array[Byte], String]("") {
        case (s: String, bytes: Array[Byte]) => s"$s${new String(bytes)}"
      }

      virusInformation.flatMap(vis => {
        logger.debug("Response from clamd: " + vis)
        Future.successful(vis.trim)
      })
    } else {
      Future.successful("Not connected to clamd")
    }
  }

  /**
   * Close the TCP socket connection to clamd
   */
  def terminate() {
    if (isConnected) {
      socket.foreach(_.close())
      out.close()
      logger.info("TCP socket to clamd is now closed")
    }
  }

}

object ClamSocket {
  def apply(): ClamSocket = new ClamSocket(ClamConfig.host, ClamConfig.port, ClamConfig.timeout)
}