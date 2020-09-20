package forex.services.oneframe

object errors {
  sealed abstract class OneFrameError(val error: String) extends Throwable with Product with Serializable

  object OneFrameError {
    final case class QuotaReachedError(override val error: String) extends OneFrameError(error)
    final case class GenericError(override val error: String) extends OneFrameError(error)
  }
}
