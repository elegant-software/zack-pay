package com.elegant.software.blitzpay.payments

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.CorsProperties
import com.elegant.software.blitzpay.config.OpenApiGroupProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication(scanBasePackages = ["com.elegant.software.blitzpay"])
@EnableConfigurationProperties(ApiVersionProperties::class, CorsProperties::class, OpenApiGroupProperties::class)
@EnableAsync
class QuickpayApplication

fun main(args: Array<String>) {
	runApplication<QuickpayApplication>(*args)
}
