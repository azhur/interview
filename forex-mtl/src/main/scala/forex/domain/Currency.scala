package forex.domain

import cats.Show

sealed abstract class Currency(val symbol: String) extends Product with Serializable

object Currency {
  case object AUD extends Currency("AUD")
  case object CAD extends Currency("CAD")
  case object CHF extends Currency("CHF")
  case object EUR extends Currency("EUR")
  case object GBP extends Currency("GBP")
  case object NZD extends Currency("NZD")
  case object JPY extends Currency("JPY")
  case object SGD extends Currency("SGD")
  case object USD extends Currency("USD")

  val values: Set[Currency] = Set(AUD, CAD, CHF, EUR, GBP, NZD, JPY, SGD, USD)

  implicit val show: Show[Currency] = Show.show(_.symbol)

  def fromString(s: String): Option[Currency] = values.find(_.symbol equalsIgnoreCase s)
}
