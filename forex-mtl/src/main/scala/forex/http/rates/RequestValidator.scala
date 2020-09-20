package forex.http.rates

import forex.domain.Currency
import forex.http.rates.Protocol.GetApiRequest
import forex.http.rates.errors.ApiError
import org.http4s.ParseFailure
import cats.data._
import cats.syntax.apply._
import cats.syntax.validated._

sealed trait RequestValidator {

  /**
    * Validates rates request input parameters.
    */
  def validateRateReq(from: Option[ValidatedNel[ParseFailure, Currency]],
                      to: Option[ValidatedNel[ParseFailure, Currency]]): Validated[ApiError, GetApiRequest] = {
    val neFrom = nonEmptyCurrencyField("from", from)
    val neTo   = nonEmptyCurrencyField("to", to)
    (neFrom, neTo)
      .mapN(GetApiRequest)
      .leftMap(errors => ApiError.ValidationError(errors.toList.map(_.sanitized).mkString(", ")))
      .ensure(ApiError.ValidationError("'from' and 'to' should not be the same"))(req => req.from != req.to)
  }

  private def nonEmptyCurrencyField(
      name: String,
      field: Option[ValidatedNel[ParseFailure, Currency]]
  ): ValidatedNel[ParseFailure, Currency] =
    field.getOrElse(
      ParseFailure(
        sanitized = s"missing '$name' required query param",
        details = s"missing '$name' required query param"
      ).invalidNel
    )
}

object RequestValidator extends RequestValidator
