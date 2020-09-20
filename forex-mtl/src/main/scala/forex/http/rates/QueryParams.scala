package forex.http.rates

import cats.data.{ NonEmptyList, Validated, ValidatedNel }
import forex.domain.Currency
import org.http4s.dsl.impl.OptionalValidatingQueryParamDecoderMatcher
import org.http4s.{ ParseFailure, QueryParamDecoder, QueryParameterValue }

object QueryParams {

  private[http] implicit val currencyQueryParam: QueryParamDecoder[Currency] =
    new QueryParamDecoder[Currency] {
      override def decode(value: QueryParameterValue): ValidatedNel[ParseFailure, Currency] =
        Validated.fromOption(
          Currency.fromString(value.value),
          NonEmptyList.one(
            ParseFailure(
              sanitized = s"'${value.value}' is not valid or is not supported currency",
              details = s"'${value.value}' is not valid or is not supported currency"
            )
          )
        )
    }

  object FromQueryParam extends OptionalValidatingQueryParamDecoderMatcher[Currency]("from")
  object ToQueryParam extends OptionalValidatingQueryParamDecoderMatcher[Currency]("to")

}
