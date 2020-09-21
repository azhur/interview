package forex.http
package rates

import java.time.{ Instant, OffsetDateTime, ZoneOffset }

import cats.effect._
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.programs.RatesProgram
import forex.programs.rates.Protocol.GetRatesRequest
import forex.programs.rates.errors.Error
import forex.programs.rates.errors.Error.RateLookupFailed
import io.circe._
import org.http4s.implicits._
import org.http4s.{ Request, _ }
import org.scalatest.Assertion
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

class RatesHttpRoutesSpec extends AnyFreeSpec with Matchers {
  "/rates endpoint" - {
    "should handle invalid query params" - {
      val failureProgram = new RatesProgram[IO] {
        override def get(request: GetRatesRequest): IO[Either[Error, Rate]] =
          IO.pure(Left(RateLookupFailed("lookup failed")))
      }
      "missing from and to" in {
        runRequest(
          uri = uri"/rates",
          program = failureProgram,
          status = Status.BadRequest,
          expectedJson = Some(
            Json.obj(
              ("error", Json.fromString("missing 'from' required query param, missing 'to' required query param"))
            )
          )
        )
      }

      "invalid from and to" in {
        runRequest(
          uri = uri"/rates?from=RUE&to=DAC",
          program = failureProgram,
          status = Status.BadRequest,
          expectedJson = Some(
            Json.obj(
              (
                "error",
                Json.fromString(
                  "'RUE' is not valid or is not supported currency, 'DAC' is not valid or is not supported currency"
                )
              )
            )
          )
        )
      }
    }
    "should handle valid request" in {
      val program = new RatesProgram[IO] {
        override def get(request: GetRatesRequest): IO[Either[Error, Rate]] =
          IO.pure(
            Right(
              Rate(
                Rate.Pair(Currency.AUD, Currency.EUR),
                Price(1.0),
                Timestamp(OffsetDateTime.ofInstant(Instant.EPOCH, ZoneOffset.UTC))
              )
            )
          )
      }

      runRequest(
        uri = uri"/rates?from=AUD&to=EUR",
        program = program,
        status = Status.Ok,
        expectedJson = Some(
          Json.obj(
            ("from", Json.fromString("AUD")),
            ("to", Json.fromString("EUR")),
            ("price", Json.fromBigDecimal(1.0)),
            ("timestamp", Json.fromString("1970-01-01T00:00:00Z")),
          )
        )
      )
    }
  }

  private def runRequest(uri: Uri, program: RatesProgram[IO], status: Status, expectedJson: Option[Json]): Assertion = {
    val routes = new RatesHttpRoutes(program).routes.orNotFound

    val request = Request[IO](uri = uri)

    val response = routes.run(request)

    check[Json](response, status, expectedJson)
  }

  // Return true if match succeeds; otherwise false
  private def check[A](actual: IO[Response[IO]], expectedStatus: Status, expectedBody: Option[A])(
      implicit ev: EntityDecoder[IO, A]
  ): Assertion = {
    val actualResp = actual.unsafeRunSync
    actualResp.status should be(expectedStatus)
    def expectEmpty = withClue("Got non empty response") {
      actualResp.body.compile.toVector.unsafeRunSync should be(empty)
    }

    def expectNonEmpty(expectedBody: A) = actualResp.as[A].unsafeRunSync should be(expectedBody)

    expectedBody.fold[Assertion](expectEmpty)(expectNonEmpty)
  }
}
