package forex.services.rates

import java.time.{ Instant, OffsetDateTime, ZoneOffset }
import java.util.concurrent.atomic.AtomicInteger

import cats.effect.IO
import cats.implicits._
import forex.services.rates.interpreters.CachingOneFrame
import org.scalatest.EitherValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._
import cats.effect._
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.services.OneFrameApi
import forex.services.oneframe.errors.OneFrameError
import forex.services.oneframe.Protocol
import forex.services.oneframe.Protocol.GetRatesResponse

import scala.concurrent.ExecutionContext

class CachingOneFrameSpec extends AnyFreeSpec with Matchers with EitherValues {
  private implicit val timer: Timer[IO]     = IO.timer(ExecutionContext.global)
  private implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "should query for all currency pairs and cache the api calls" in {
    val apiCalls = new AtomicInteger(0)
    val oneFrameApi = new OneFrameApi[IO] {
      override def getRates(pairs: Set[Rate.Pair]): IO[Either[OneFrameError, List[Protocol.GetRatesResponse]]] = {
        pairs should be(CachingOneFrame.AllCurrencyPairs)

        apiCalls.incrementAndGet()

        IO.pure(
          Right(
            List(
              GetRatesResponse(
                from = Currency.USD,
                to = Currency.EUR,
                bid = Price(1.1),
                ask = Price(2.1),
                price = Price(3.1),
                timeStamp = epochTimestamp()
              ),
              GetRatesResponse(
                from = Currency.EUR,
                to = Currency.USD,
                bid = Price(1.1),
                ask = Price(2.1),
                price = Price(3.1),
                timeStamp = epochTimestamp()
              )
            )
          )
        )
      }
    }

    val service =
      CachingOneFrame.autoReloading[IO](oneFrameApi, dataMaxAge = 5.minutes).compile.toVector.unsafeRunSync().head

    val response1 = IO.shift *> service.get(Rate.Pair(from = Currency.USD, to = Currency.EUR))
    val response2 = IO.shift *> service.get(Rate.Pair(from = Currency.EUR, to = Currency.USD))

    val results = List(response1, response2).parSequence.unsafeRunSync()

    results should have length (2)

    apiCalls.get() shouldBe 1

    results.foreach { result =>
      result should be('right)
    }
  }

  private def epochTimestamp(): Timestamp =
    Timestamp(OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC))
}
