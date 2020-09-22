package forex.services.oneframe

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime

import cats.effect.concurrent.Ref
import cats.effect.{ IO, Resource }
import forex.config.{ HttpConfig, OneFrameApiConfig }
import forex.domain.{ Currency, Price, Rate, Timestamp }
import forex.services.oneframe.Protocol.GetRatesResponse
import forex.services.oneframe.errors.OneFrameError
import forex.services.oneframe.interpreters.LiveOneFrameClient
import fs2.Stream
import io.circe.Json
import org.http4s.client.Client
import org.http4s.{ Header, Headers, Request, Response, Status }
import org.scalatest.{ Assertion, EitherValues }
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.duration._

class LiveOneFrameClientSpec extends AnyFreeSpec with Matchers with EitherValues {
  private val config = OneFrameApiConfig(
    http = HttpConfig(
      host = "test",
      port = 11,
      timeout = 1.second
    ),
    token = "secret",
    retries = 3,
    retryMaxWait = 1.second,
    dataMaxAge = 1.minute
  )

  "should correctly populate the request and deserialize the response" in {
    val pair1      = Rate.Pair(from = Currency.USD, to = Currency.EUR)
    val pair2      = Rate.Pair(from = Currency.CAD, to = Currency.JPY)
    val currencies = Set(pair1, pair2)

    val oneFrameResponse = Json.arr(
      Json.obj(
        ("from", Json.fromString(pair1.from.symbol)),
        ("to", Json.fromString(pair1.to.symbol)),
        ("bid", Json.fromBigDecimal(1.1)),
        ("ask", Json.fromBigDecimal(2.1)),
        ("price", Json.fromBigDecimal(3.1)),
        ("time_stamp", Json.fromString("2020-09-22T06:07:07.627Z")),
      ),
      Json.obj(
        ("from", Json.fromString(pair2.from.symbol)),
        ("to", Json.fromString(pair2.to.symbol)),
        ("bid", Json.fromBigDecimal(4.1)),
        ("ask", Json.fromBigDecimal(5.1)),
        ("price", Json.fromBigDecimal(6.1)),
        ("time_stamp", Json.fromString("2020-09-22T06:07:07.628Z")),
      ),
    )

    val expectedResponse = Right(
      List(
        GetRatesResponse(
          from = pair1.from,
          to = pair1.to,
          bid = Price(1.1),
          ask = Price(2.1),
          price = Price(3.1),
          timeStamp = timestampFromString("2020-09-22T06:07:07.627Z")
        ),
        GetRatesResponse(
          from = pair2.from,
          to = pair2.to,
          bid = Price(4.1),
          ask = Price(5.1),
          price = Price(6.1),
          timeStamp = timestampFromString("2020-09-22T06:07:07.628Z")
        )
      )
    )

    val assertion = for {
      state <- Ref[IO].of(Request[IO]())
      httpClient = createHttpClient(Status.Ok, oneFrameResponse, state)
      client     = new LiveOneFrameClient[IO](config, httpClient)
      response <- client.getRates(currencies)
      updatedRequest <- state.get
    } yield {
      assertRequest(updatedRequest, currencies)
      response should be(expectedResponse)
    }

    assertion.unsafeRunSync()
  }

  "should fallback to custom errors on non OK statuses" in {
    val pair1      = Rate.Pair(from = Currency.USD, to = Currency.EUR)
    val currencies = Set(pair1)

    val oneFrameResponse = Json.Null

    val expectedResponse = Left(OneFrameError.GenericError("unexpected HTTP status: 500 Internal Server Error"))

    val assertion = for {
      state <- Ref[IO].of(Request[IO]())
      httpClient = createHttpClient(Status.InternalServerError, oneFrameResponse, state)
      client     = new LiveOneFrameClient[IO](config, httpClient)
      response <- client.getRates(currencies)
      updatedRequest <- state.get
    } yield {
      assertRequest(updatedRequest, currencies)
      response should be(expectedResponse)
    }

    assertion.unsafeRunSync()
  }

  "should fallback to custom errors on non-parseable responses" in {
    val pair1      = Rate.Pair(from = Currency.USD, to = Currency.EUR)
    val currencies = Set(pair1)

    val oneFrameResponse = Json.Null

    val expectedResponse = Left(OneFrameError.GenericError("Invalid message body: Could not decode JSON: null"))

    val assertion = for {
      state <- Ref[IO].of(Request[IO]())
      httpClient = createHttpClient(Status.Ok, oneFrameResponse, state)
      client     = new LiveOneFrameClient[IO](config, httpClient)
      response <- client.getRates(currencies)
      updatedRequest <- state.get
    } yield {
      assertRequest(updatedRequest, currencies)
      response should be(expectedResponse)
    }

    assertion.unsafeRunSync()
  }

  private def assertRequest(actualRequest: Request[IO], expectedPairs: Set[Rate.Pair]): Assertion = {
    val expectedQuery = expectedPairs.map(pair => s"pair=${pair.from.symbol}${pair.to.symbol}").mkString("&")
    actualRequest.uri.renderString should be(s"http://test:11/rates?$expectedQuery")
    actualRequest.headers.toList should contain(Header("token", "secret"))
  }

  private def createHttpClient(status: Status, response: Json, state: Ref[IO, Request[IO]]): Client[IO] = {
    def createResponse: Resource[IO, Response[IO]] =
      Resource.pure[IO, Response[IO]](
        Response[IO](
          status = status,
          body = Stream[IO, Byte](response.spaces2.getBytes(StandardCharsets.UTF_8): _*),
          headers = Headers.of(Header("Content-Type", "application/json"))
        )
      )

    Client[IO] { request =>
      for {
        _ <- Resource.liftF[IO, Unit](state.set(request))
        response <- createResponse
      } yield response
    }
  }

  private def timestampFromString(str: String): Timestamp =
    Timestamp(
      OffsetDateTime.parse(str)
    )
}
