package forex

import forex.domain.Currency.show
import forex.domain.Rate.Pair
import io.circe.{ Decoder, Encoder, Json }
import io.circe.generic.extras.decoding.{ EnumerationDecoder, UnwrappedDecoder }
import io.circe.generic.extras.encoding.{ EnumerationEncoder, UnwrappedEncoder }
import io.circe.generic.semiauto.{ deriveDecoder, deriveEncoder }

package object domain {
  implicit def valueClassEncoder[A: UnwrappedEncoder]: Encoder[A] = implicitly
  implicit def valueClassDecoder[A: UnwrappedDecoder]: Decoder[A] = implicitly

  implicit def enumEncoder[A: EnumerationEncoder]: Encoder[A] = implicitly
  implicit def enumDecoder[A: EnumerationDecoder]: Decoder[A] = implicitly

  implicit val currencyEncoder: Encoder[Currency] =
    Encoder.instance[Currency] { show.show _ andThen Json.fromString }

  implicit val currencyDecoder: Decoder[Currency] =
    Decoder.decodeString.emap(str => Currency.fromString(str).toRight(s"'$str' is unknown currency!"))

  implicit val pairEncoder: Encoder[Pair] = deriveEncoder[Pair]
  implicit val pairDecoder: Decoder[Pair] = deriveDecoder[Pair]

  implicit val rateEncoder: Encoder[Rate] = deriveEncoder[Rate]
  implicit val rateDecoder: Decoder[Rate] = deriveDecoder[Rate]
}
