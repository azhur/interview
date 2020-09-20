package forex.services.rates.interpreters

import cats.{ FlatMap, Functor }
import cats.effect.{ ConcurrentEffect, Timer }
import cats.syntax.functor._
import cats.syntax.flatMap._
import forex.config.OneFrameApiConfig
import forex.domain.{ Currency, Rate }
import forex.services.OneFrameApi
import forex.services.oneframe.Protocol.GetRatesResponse
import forex.services.rates.Algebra
import forex.services.rates.errors._
import io.chrisdavenport.mules.{ Lookup, TimeSpec }
import io.chrisdavenport.mules.reload.AutoFetchingCache
import io.chrisdavenport.mules.reload.AutoFetchingCache.RefreshConfig

import scala.concurrent.duration._

class CacheableOneFrame[F[_]: Functor: FlatMap] private (cache: Lookup[F, None.type, List[GetRatesResponse]])
    extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache
      .lookup(None)
      .map { items =>
        println(items)
        items
          .flatMap { responses =>
            responses
              .find(r => r.from == pair.from && r.to == pair.to)
              .map(r => Rate(Rate.Pair(r.from, r.to), r.price, r.timeStamp))
          }
          .toRight(Error.CacheMiss(s"Cache doesn't contain $pair"))
      }

}

object CacheableOneFrame {
  def live[F[_]: Functor: ConcurrentEffect: Timer: FlatMap](
      config: OneFrameApiConfig,
      oneFrameApi: OneFrameApi[F]
  ): F[Algebra[F]] =
    getAllRates(oneFrameApi)
      .flatMap { items =>
        // map from either to effect error so the cache can reload failures
        AutoFetchingCache
          .createCache[F, None.type, List[GetRatesResponse]](
            defaultExpiration = TimeSpec.fromDuration(4.minutes),
            refreshConfig =
              Some(RefreshConfig(period = TimeSpec.unsafeFromDuration(1.second), maxParallelRefresh = Some(1)))
          )(_ => getAllRates(oneFrameApi))
          .flatMap(cache => cache.insertWithTimeout(TimeSpec.fromDuration(4.minutes))(None, items).map(_ => cache))
          .map(new CacheableOneFrame[F](_))
      }

  private def getAllRates[F[_]: Functor: ConcurrentEffect](oneFrameApi: OneFrameApi[F]): F[List[GetRatesResponse]] = {
    println("GET ALL RATES" + AllCurrencyPairs.size.toString)
    oneFrameApi.getRates(AllCurrencyPairs).flatMap(ConcurrentEffect[F].fromEither)
  }

  private lazy val AllCurrencyPairs = {
    val pairs        = Currency.values.toList.combinations(2).toList
    val reversePairs = pairs.map(_.reverse)
    (pairs ++ reversePairs).map(l => Rate.Pair(l.head, l.tail.head)).toSet
  }
}
