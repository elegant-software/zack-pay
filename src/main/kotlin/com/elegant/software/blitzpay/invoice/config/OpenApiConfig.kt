package com.elegant.software.blitzpay.invoice.config

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.OpenApiGroupProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import com.elegant.software.blitzpay.invoice.InvoiceController
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class InvoiceOpenApiConfig(
    private val apiVersionProperties: ApiVersionProperties,
    private val openApiGroupProperties: OpenApiGroupProperties,
) {

    @Bean
    fun invoiceApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.invoice.label)
            .packagesToScan(InvoiceController::class.java.packageName)
            .pathsToMatch(
                "/{version}/invoices/**",
                "/{version}/shopper/orders/*/invoice",
                "/{version}/merchant/orders/*/invoice",
            )
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info().title("BlitzPay — Invoice API").version("v${apiVersionProperties.versions.invoice}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.invoice)
            }
            .build()
}
