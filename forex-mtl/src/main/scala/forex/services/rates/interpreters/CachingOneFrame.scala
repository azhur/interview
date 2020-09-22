package forex.services.rates.interpreters

import cats.Functor
import cats.effect.concurrent.Ref
import cats.effect.{ Concurrent, Sync, Timer }
import cats.syntax.flatMap._
import cats.syntax.functor._
import forex.domain.{ Currency, Rate }
import forex.services.OneFrameApi
import forex.services.oneframe.Protocol.GetRatesResponse
import forex.services.rates.Algebra
import forex.services.rates.errors._

import scala.concurrent.duration._

class CachingOneFrame[F[_]: Functor] private (cache: Ref[F, List[GetRatesResponse]]) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache.get.map { responses =>
      responses
        .find(r => r.from == pair.from && r.to == pair.to)
        .map(r => Rate(Rate.Pair(r.from, r.to), r.price, r.timeStamp))
        .toRight(Error.CacheMiss(s"Cache doesn't contain $pair"))
    }

}

object CachingOneFrame {
  def autoReloading[F[_]: Functor: Sync: Timer: Concurrent](api: OneFrameApi[F],
                                                            dataMaxAge: FiniteDuration): fs2.Stream[F, Algebra[F]] = {
    def loader: F[List[GetRatesResponse]] =
      api
        .getRates(AllCurrencyPairs)
        .flatMap(Sync[F].fromEither)

    def restartStream(stream: fs2.Stream[F, Unit]): fs2.Stream[F, Unit] = stream.handleErrorWith { _ =>
      restartStream(stream)
    }

    for {
      initialValue <- fs2.Stream.eval(loader)
      ref <- fs2.Stream.eval(Ref.of[F, List[GetRatesResponse]](initialValue))
      autoLoader = restartStream(
        fs2.Stream
          .awakeEvery[F](dataMaxAge.minus(1.minute)) // todo 1 minute for request completion, move it to conf file
          .evalMap(_ => loader.flatMap(v => ref.set(v)))
      )

      lookup = fs2.Stream.eval(Sync[F].pure(new CachingOneFrame[F](ref)))
      cache <- lookup.concurrently(autoLoader)
    } yield cache
  }

  private[rates] lazy val AllCurrencyPairs = {
    val pairs        = Currency.values.toList.combinations(2).toList
    val reversePairs = pairs.map(_.reverse)
    (pairs ++ reversePairs).map(l => Rate.Pair(l.head, l.tail.head)).toSet
  }
}
