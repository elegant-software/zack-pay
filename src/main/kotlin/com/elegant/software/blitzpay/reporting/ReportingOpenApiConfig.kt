package com.elegant.software.blitzpay.reporting

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.OpenApiGroupProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ReportingOpenApiConfig(
    private val apiVersionProperties: ApiVersionProperties,
    private val openApiGroupProperties: OpenApiGroupProperties,
) {

    @Bean
    fun reportingApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.reporting.label)
            .pathsToMatch("/{version}/reporting/**")
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info()
                    .title("BlitzPay — Reporting & Dashboards API")
                    .version("v${apiVersionProperties.versions.reporting}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.reporting)
            }
            .build()
}
