package forex.services.oneframe

import forex.domain._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.java8.time._

object Protocol {
  final case class GetRatesRequest(pairs: Set[Rate.Pair])
  final case class GetRatesResponse(from: Currency,
                                    to: Currency,
                                    ask: Price,
                                    bid: Price,
                                    price: Price,
                                    timeStamp: Timestamp)

  object GetRatesResponse {
    implicit val responseEncoder: Encoder[GetRatesResponse] =
      deriveEncoder[GetRatesResponse]

    implicit val responseDecoder: Decoder[GetRatesResponse] =
      Decoder.forProduct6[GetRatesResponse, Currency, Currency, Price, Price, Price, Timestamp](
        "from",
        "to",
        "ask",
        "bid",
        "price",
        "time_stamp"
      )(GetRatesResponse.apply)

  }

}
