package forex.services.rates

import cats.effect.{ ConcurrentEffect, Timer }
import cats.{ Applicative, FlatMap, Functor }
import forex.config.OneFrameApiConfig
import forex.services.OneFrameApi
import interpreters._

object Interpreters {
  def dummy[F[_]: Applicative](): Algebra[F] = new OneFrameDummy[F]()
  def cacheable[F[_]: Functor: ConcurrentEffect: Timer: FlatMap](config: OneFrameApiConfig,
                                                                 oneFrameApi: OneFrameApi[F]): F[Algebra[F]] =
    CacheableOneFrame.live[F](config, oneFrameApi)
}
