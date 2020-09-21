package forex.services.oneframe.interpreters

import cats.effect.Sync
import cats.syntax.either._
import cats.syntax.functor._
import forex.config.OneFrameApiConfig
import forex.domain.Rate
import forex.services.oneframe.{ errors, Algebra, Protocol }
import io.circe.Decoder
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{ EntityDecoder, Header, Method, Request, Uri }

class LiveOneFrameClient[F[_]: Sync](config: OneFrameApiConfig, httpClient: Client[F])
    extends Http4sClientDsl[F]
    with Algebra[F] {

  private lazy val RatesBasePath = Uri.unsafeFromString(s"http://${config.http.host}:${config.http.port}/rates")

  private implicit def listJsonDecoder[A <: Product](implicit ed: Decoder[List[A]]): EntityDecoder[F, List[A]] =
    jsonOf[F, List[A]]

  override def getRates(pairs: Set[Rate.Pair]): F[Either[errors.OneFrameError, List[Protocol.GetRatesResponse]]] = {
    import cats.syntax.applicativeError._

    val uriWithParams =
      RatesBasePath.setQueryParams(Map("pair" -> pairs.toSeq.map(pair => s"${pair.from.symbol}${pair.to.symbol}")))

    val request = Request[F]()
      .withUri(uriWithParams)
      .withMethod(Method.GET)
      .withHeaders(Header("token", config.token))

    httpClient
      .expect[List[Protocol.GetRatesResponse]](request)
      .map(_.asRight[errors.OneFrameError])
      .handleErrorWith { error =>
        Sync[F].pure(errors.OneFrameError.GenericError(error.getMessage).asLeft)
      }
  }
}
