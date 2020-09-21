package forex.programs.rates

import java.time.{ Instant, OffsetDateTime, ZoneOffset }

import cats.effect.IO
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.programs.rates.Protocol.GetRatesRequest
import forex.services.RatesService
import forex.services.rates.{ errors => serrors }
import forex.programs.rates.{ errors => perrors }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RatesProgramSpec extends AnyFreeSpec with Matchers {
  "rate program" - {
    "should convert service error to program error" in {
      val ratesService = new RatesService[IO] {
        override def get(pair: Rate.Pair): IO[Either[serrors.Error, Rate]] =
          IO.pure(Left(serrors.Error.OneFrameLookupFailed("lookup failed")))
      }
      val program  = Program[IO](ratesService)
      val response = program.get(GetRatesRequest(Currency.USD, Currency.EUR)).unsafeRunSync()
      response shouldBe Left(
        perrors.Error.RateLookupFailed("lookup failed")
      )
    }

    "should propagate success results" in {
      val expectedResponse = Right(
        Rate(
          Rate.Pair(Currency.AUD, Currency.EUR),
          Price(1.0),
          Timestamp(OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC))
        )
      )

      val ratesService = new RatesService[IO] {
        override def get(pair: Rate.Pair): IO[Either[serrors.Error, Rate]] =
          IO.pure(expectedResponse)
      }

      val program  = Program[IO](ratesService)
      val response = program.get(GetRatesRequest(Currency.USD, Currency.EUR)).unsafeRunSync()
      response shouldBe expectedResponse

    }
  }
}
