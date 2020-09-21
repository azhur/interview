package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    oneFrameApi: OneFrameApiConfig
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)

case class OneFrameApiConfig(http: HttpConfig,
                             token: String,
                             retries: Int,
                             retryMaxWait: FiniteDuration,
                             dataMaxAge: FiniteDuration)
