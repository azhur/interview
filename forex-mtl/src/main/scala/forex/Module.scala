package forex

import cats.Functor
import cats.effect.{ ConcurrentEffect, Sync, Timer }
import forex.config.ApplicationConfig
import forex.http.rates.RatesHttpRoutes
import forex.services._
import forex.programs._
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.middleware.{ AutoSlash, Timeout }
import cats.syntax.functor._

class Module[F[_]: ConcurrentEffect: Timer: Sync: Functor](config: ApplicationConfig) {

  private val oneFrameApiService: OneFrameApi[F] = OneFrameServices.live[F](config.oneFrameApi)

  private val cacheableRateService: F[RatesService[F]] =
    RatesServices.cacheable[F](config.oneFrameApi, oneFrameApiService)

  private val ratesProgram: F[RatesProgram[F]] = cacheableRateService.map(RatesProgram[F])

  private val ratesHttpRoutes: F[HttpRoutes[F]] = ratesProgram.map(rp => new RatesHttpRoutes[F](rp).routes)

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

  private val http: F[HttpRoutes[F]] = ratesHttpRoutes

  val httpApp: F[HttpApp[F]] = http.map(http => appMiddleware(routesMiddleware(http).orNotFound))

}
