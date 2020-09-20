package forex.http.rates

import org.http4s.Status
import io.circe.Encoder
import forex.programs.rates.errors.{ Error => RatesProgramError }

object errors {
  sealed abstract class ApiError(val status: Status, val error: String) extends Throwable with Product with Serializable

  object ApiError {
    final case class ValidationError(override val error: String)
        extends ApiError(status = Status.BadRequest, error = error)
    final case class InternalServerError(override val error: String)
        extends ApiError(status = Status.InternalServerError, error = error)

    def fromProgramError(pe: RatesProgramError): ApiError = pe match {
      case RatesProgramError.RateLookupFailed(msg) => InternalServerError(msg)
      case unknown                                 =>
        // as the callers are internal api, it should be ok to expose the message
        InternalServerError(unknown.getMessage)
    }

    implicit val apiErrorEncoder: Encoder[ApiError] =
      Encoder.forProduct1[ApiError, String]("error")(_.error)
  }
}
