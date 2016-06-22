package net.scalytica.clammyscan

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.io.Inet
import akka.io.Inet.SO.SendBufferSize
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString
import net.scalytica.clammyscan.ClamProtocol._
import net.scalytica.clammyscan.UnsignedInt._
import org.slf4j.LoggerFactory

import scala.collection.immutable
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

/**
 *
 * @param host
 * @param port
 * @param timeout
 * @param ec
 * @param system
 * @param mat
 */
class ClamIO(
  host: String,
  port: Int,
  timeout: Duration
)(implicit ec: ExecutionContext, system: ActorSystem, mat: Materializer) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  private val inetAddr = new InetSocketAddress(host, port)

  // Defines the TCP socket connection to ClamAV.
  private val connection = Tcp().outgoingConnection(
    remoteAddress = inetAddr,
    connectTimeout = timeout,
    options = ClamIO.socketOpts
  )

  /*
    TODO: Somehow I need to catch the error cases and return a proper result.
    TODO: The proper result should then be a ClamSink that is cancelled and
    TODO: has the correct error set on it's Left side in the ClamResponse.

    basically => Sink[ByteString, Future[Left[ClamError]]]
   */

  private var commandInitiated: Boolean = false

  /**
   * The ClamAV protocol is very specific. This Flow aims to handle that.
   *
   * All communications must start with a specific command. Depending on the
   * command a certain sequence of bytes and chunks are expected.
   * For example, the INSTREAM command expects each chunk to come after
   * a sequence of 4 bytes with the length of the following chunk as an
   * unsigned integer.
   */
  val prepare = Flow[ByteString].mapConcat { bs =>
    val builder = immutable.Seq.newBuilder[ByteString]
    if (!commandInitiated) {
      builder += Instream.cmd
      commandInitiated = true
    }

    if (bs != Zero && !ByteStrCommand.isCommand(bs))
      builder += unsignedInt(bs.length)

    builder += bs

    builder.result()
  }.concat(Source.single(Zero)).via(connection)

  /**
   *
   * @param fname
   * @return
   */
  private[this] def sink(fname: String): Sink[ByteString, Future[ScanState]] =
    Sink.fold[ScanState, ByteString](ScanState(fname)) {
      case (state: ScanState, chunk: ByteString) =>
        logger.debug(s"Processing new chunk ${state.chunkNum + 1}...\n")
        state.append(chunk)
    }

  /**
   *
   * @param filename
   * @return
   */
  def scan(filename: String): ClamSink = {
    logger.debug(s"Preparing to scan file $filename with clamd...")
    prepare.toMat(sink(filename))(Keep.right)
      .mapMaterializedValue { ss =>
        logger.debug("Materializing result...")
        ss.map(_.validate)
      }
  }

  private case class ScanState(
    filename: String,
    chunkNum: Int = 0,
    result: String = ""
  ) {

    def append(chunk: ByteString): ScanState = {
      copy(
        chunkNum = chunkNum + 1,
        result = s"$result${chunk.utf8String}"
      )
    }

    def validate: ClamResponse = {
      val res = result.trim
      if (ClamProtocol.okResponse.equals(res)) {
        logger.debug(s"No viruses found in $filename")
        Right(FileOk())
      } else if (ClamProtocol.unknownCommand.equals(res)) {
        logger.warn(s"Command not recognized: $res")
        Left(ScanError(result))
      } else {
        logger.warn(s"Virus detected in $filename - $res")
        Left(VirusFound(res))
      }
    }

  }

}

object ClamIO {

  val chunkSize = 262144
  val socketOpts = immutable.Traversable[Inet.SocketOption](
    SendBufferSize(chunkSize)
  )

  def apply()(
    implicit
    ec: ExecutionContext,
    as: ActorSystem,
    mat: Materializer
  ): ClamIO =
    new ClamIO(ClamConfig.host, ClamConfig.port, ClamConfig.timeoutDuration)
}