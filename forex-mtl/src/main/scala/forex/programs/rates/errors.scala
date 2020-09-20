package forex.programs.rates

import forex.services.rates.errors.{ Error => RatesServiceError }
import forex.services.oneframe.errors.OneFrameError

object errors {

  sealed trait Error extends Exception
  object Error {
    final case class RateLookupFailed(msg: String) extends Error
  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case RatesServiceError.OneFrameLookupFailed(msg) => Error.RateLookupFailed(msg)
    case RatesServiceError.CacheMiss(msg)            => Error.RateLookupFailed(msg)
  }

  def toProgramError(error: OneFrameError): Error = error match {
    case OneFrameError.QuotaReachedError(msg) => Error.RateLookupFailed(msg)
    case OneFrameError.GenericError(msg)      => Error.RateLookupFailed(msg)
  }
}
