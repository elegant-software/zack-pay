package com.elegant.software.blitzpay.payments.truelayer.config

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.OpenApiGroupProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["truelayer.enabled"], havingValue = "true")
class TruelayerOpenApiConfig(
    private val apiVersionProperties: ApiVersionProperties,
    private val openApiGroupProperties: OpenApiGroupProperties,
) {

    @Bean
    fun truelayerApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.truelayer.label)
            .packagesToScan("com.elegant.software.blitzpay.payments.truelayer")
            .pathsToMatch("/{version}/webhooks/truelayer/**")
            .addOpenApiCustomizer { openApi ->
                openApi.info.title = openApiGroupProperties.groups.truelayer.label
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.truelayer)
            }
            .build()
}
