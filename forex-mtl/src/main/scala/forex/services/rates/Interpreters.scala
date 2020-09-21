package forex.services.rates

import cats.effect.{ Concurrent, Sync, Timer }
import cats.{ Applicative, Functor }
import forex.services.OneFrameApi
import forex.services.rates.interpreters._

import scala.concurrent.duration.FiniteDuration

object Interpreters {
  def dummy[F[_]: Applicative](): Algebra[F] = new OneFrameDummy[F]()

  def caching[F[_]: Functor: Sync: Timer: Concurrent](oneFrameApi: OneFrameApi[F],
                                                      dataMaxAge: FiniteDuration): fs2.Stream[F, Algebra[F]] =
    CachingOneFrame.autoReloading(oneFrameApi, dataMaxAge)
}
