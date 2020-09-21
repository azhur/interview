package forex.services.oneframe

import cats.effect.Sync
import forex.config.OneFrameApiConfig
import forex.services.oneframe.interpreters.LiveOneFrameClient
import org.http4s.client.Client

object Interpreters {
  def live[F[_]: Sync](config: OneFrameApiConfig, client: Client[F]): Algebra[F] =
    new LiveOneFrameClient[F](config, client)
}
