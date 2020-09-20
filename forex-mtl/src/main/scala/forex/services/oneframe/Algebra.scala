package forex.services.oneframe

import forex.domain.Rate
import forex.services.oneframe.Protocol.GetRatesResponse
import forex.services.oneframe.errors.OneFrameError

trait Algebra[F[_]] {
  def getRates(pairs: Set[Rate.Pair]): F[Either[OneFrameError, List[GetRatesResponse]]]
}
