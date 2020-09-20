package forex.config

import cats.effect.Sync
import fs2.Stream
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderException
import pureconfig.generic.auto._

object Config {

  /**
    * @param path the property path inside the default configuration
    */
  def stream[F[_]: Sync](path: String): Stream[F, ApplicationConfig] =
    Stream.eval(
      Sync[F].suspend(
        ConfigSource.default.at(path).load[ApplicationConfig] match {
          case Left(failures) => Sync[F].raiseError(new ConfigReaderException[ApplicationConfig](failures))
          case Right(value)   => Sync[F].pure(value)
        }
      )
    )

}
