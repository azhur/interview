package forex

package object services {
  type RatesService[F[_]] = rates.Algebra[F]
  type OneFrameApi[F[_]]  = oneframe.Algebra[F]

  final val RatesServices    = rates.Interpreters
  final val OneFrameServices = oneframe.Interpreters
}
