package forex.services.oneframe

import cats.Functor
import cats.effect.{ ConcurrentEffect, Sync, Timer }
import forex.config.OneFrameApiConfig
import forex.services.oneframe.interpreters.LiveOneFrameClient

object Interpreters {
  def live[F[_]: Sync: Functor: ConcurrentEffect: Timer](config: OneFrameApiConfig): Algebra[F] =
    new LiveOneFrameClient[F](config)
}
