package forex

import cats.Functor
import cats.effect.{ ConcurrentEffect, Sync, Timer }
import forex.config.{ ApplicationConfig, OneFrameApiConfig }
import forex.http.rates.RatesHttpRoutes
import forex.programs._
import forex.services._
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.middleware.{ Retry, RetryPolicy }
import org.http4s.implicits._
import org.http4s.server.middleware.{ AutoSlash, Timeout }

import scala.concurrent.ExecutionContext

class Module[F[_]: ConcurrentEffect: Timer: Sync: Functor](config: ApplicationConfig, oneFrameClient: Client[F]) {
  type SF[T] = fs2.Stream[F, T]

  private val oneFrameApiService: OneFrameApi[F] = OneFrameServices.live[F](config.oneFrameApi, oneFrameClient)

  private val cacheableRateService: SF[RatesService[F]] =
    RatesServices.caching[F](oneFrameApiService, config.oneFrameApi.dataMaxAge)

  private val ratesProgram: SF[RatesProgram[F]] = cacheableRateService.map(RatesProgram[F])

  private val ratesHttpRoutes: SF[HttpRoutes[F]] = ratesProgram.map(new RatesHttpRoutes[F](_).routes)

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.timeout)(http)
  }

  private val http: SF[HttpRoutes[F]] = ratesHttpRoutes

  val httpApp: SF[HttpApp[F]] = http.map(http => appMiddleware(routesMiddleware(http).orNotFound))

}

object Module {
  def buildOneFrameHttpClient[F[_]: ConcurrentEffect: Timer](config: OneFrameApiConfig): fs2.Stream[F, Client[F]] =
    // todo use separate EC?
    BlazeClientBuilder[F](ExecutionContext.global)
      .withConnectTimeout(config.http.timeout)
      .withRequestTimeout(config.http.timeout)
      .stream
      .map { client =>
        val retryPolicy =
          RetryPolicy[F](
            RetryPolicy
              .exponentialBackoff(maxWait = config.retryMaxWait, maxRetry = config.retries)
          )
        Retry[F](retryPolicy)(client)
      }
}
