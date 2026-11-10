package com.elegant.software.blitzpay.merchant.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Settings for Tables and Table Status. */
@ConfigurationProperties(prefix = "blitzpay.tables")
data class TableProperties(
    /** An Occupied Table is freed automatically when no order was placed at it for this long. */
    val autoClear: Duration = Duration.ofHours(4),
    /** Public storefront base URL; Table Links are `{storefrontUrl}/t/{tableCode}`. */
    val storefrontUrl: String = "http://localhost:4201",
)

@Configuration
@EnableConfigurationProperties(TableProperties::class)
class TableConfig
