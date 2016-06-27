package net.scalytica.clammyscan

import scala.util.control.NoStackTrace

case class ClammyException(
  scanError: ScanError
) extends Exception(scanError.message) with NoStackTrace