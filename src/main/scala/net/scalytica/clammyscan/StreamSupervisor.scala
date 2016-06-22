package net.scalytica.clammyscan

import akka.stream.Supervision.{Decider, Stop}
import akka.stream._
import org.slf4j.LoggerFactory

private[clammyscan] object StreamSupervisor {

  private[this] val logger = LoggerFactory.getLogger(this.getClass)

  val decider: Decider = {
    case con: ConnectionException =>
      logger.warn(couldNotConnect.message, con)
      Stop

    case bindFail: BindFailedException =>
      logger.warn(s"${couldNotConnect.message}", bindFail)
      Stop
    case err =>
      logger.error(unhandledException, err)
      Stop
  }

}
