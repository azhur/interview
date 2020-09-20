package forex.http
package rates

import cats.data.Validated
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import forex.http.rates.errors.ApiError
import forex.programs.RatesProgram
import forex.programs.rates.{ Protocol => RatesProgramProtocol }
import org.http4s.{ EntityEncoder, HttpRoutes, Response }
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {

  import Converters._, QueryParams._, Protocol._

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? FromQueryParam(from) +& ToQueryParam(to) =>
      RequestValidator.validateRateReq(from, to) match {
        case Validated.Valid(req) =>
          rates
            .get(RatesProgramProtocol.GetRatesRequest(req.from, req.to))
            .map(_.left.map(ApiError.fromProgramError))
            .flatMap {
              case Left(value)  => toErrorResponse(value)
              case Right(value) => Ok(value.asGetApiResponse)
            }

        case Validated.Invalid(e) =>
          toErrorResponse(e)
      }
  }

  private def toErrorResponse(error: ApiError): F[Response[F]] =
    Sync[F].pure(Response(error.status, body = implicitly[EntityEncoder[F, ApiError]].toEntity(error).body))

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
