package forex.services.oneframe.interpreters

import cats.Functor
import cats.effect.{ ConcurrentEffect, Sync, Timer }
import forex.domain.Rate
import forex.services.oneframe.{ errors, Algebra, Protocol }
import org.http4s.{ EntityDecoder, Header, Method, Query, Request, Uri }
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import cats.syntax.functor._
import forex.config.OneFrameApiConfig
import forex.services.oneframe.interpreters.LiveOneFrameClient.withClient
import io.circe.Decoder
import org.http4s.circe.jsonOf
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.{ Retry, RetryPolicy }

import scala.concurrent.ExecutionContext

class LiveOneFrameClient[F[_]: Sync: Functor: ConcurrentEffect: Timer](config: OneFrameApiConfig)
    extends Http4sClientDsl[F]
    with Algebra[F] {

  private lazy val RatesBasePath = s"http://${config.http.host}:${config.http.port}/rates"

  private implicit def listJsonDecoder[A <: Product](implicit ed: Decoder[List[A]]): EntityDecoder[F, List[A]] =
    jsonOf[F, List[A]]

  override def getRates(pairs: Set[Rate.Pair]): F[Either[errors.OneFrameError, List[Protocol.GetRatesResponse]]] = {
    import cats.syntax.applicativeError._
    val query = Query.fromPairs(pairs.toSeq.map(pair => ("pair", s"${pair.from.symbol}${pair.to.symbol}")): _*)

    val uriWithParams = Uri(path = RatesBasePath, query = query)

    println(uriWithParams.query.pairs.size)

    val request = Request[F]()
      .withUri(uriWithParams)
      .withMethod(Method.GET)
      .withHeaders(Header("token", config.token))

    withClient[F, Either[errors.OneFrameError, List[Protocol.GetRatesResponse]]](config) { httpClient =>
      httpClient.expect[List[Protocol.GetRatesResponse]](request).map(Right.apply)
    }.handleErrorWith { error =>
      Sync[F].pure(Left(errors.OneFrameError.GenericError(error.getMessage)))
    }
  }
}

object LiveOneFrameClient {

  def withClient[F[_]: ConcurrentEffect: Timer, T](config: OneFrameApiConfig)(f: Client[F] => F[T]): F[T] =
    BlazeClientBuilder[F](ExecutionContext.global)
      .withConnectTimeout(config.http.timeout)
      .withRequestTimeout(config.http.timeout)
      .resource
      .use { client =>
        val retryPolicy =
          RetryPolicy[F](RetryPolicy.exponentialBackoff(maxWait = config.retryMaxWait, maxRetry = config.retries))

        f(Retry[F](retryPolicy)(client))
      }

}
