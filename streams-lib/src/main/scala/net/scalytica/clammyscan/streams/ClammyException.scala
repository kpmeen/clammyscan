package net.scalytica.clammyscan.streams

import scala.util.control.NoStackTrace

/**
 * When it is absolutely necessary to throw an Exception, use this one.
 *
 * @param scanError any subtype of ScanError
 */
case class ClammyException(scanError: ScanError)
    extends Exception(scanError.message)
    with NoStackTrace
