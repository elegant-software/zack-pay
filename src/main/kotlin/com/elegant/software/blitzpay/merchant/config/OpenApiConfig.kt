package com.elegant.software.blitzpay.merchant.config

import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.OpenApiGroupProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import com.elegant.software.blitzpay.merchant.web.MerchantOnboardingController
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MerchantOpenApiConfig(
    private val apiVersionProperties: ApiVersionProperties,
    private val openApiGroupProperties: OpenApiGroupProperties,
) {

    @Bean
    fun merchantApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.merchant.label)
            .packagesToScan(MerchantOnboardingController::class.java.packageName)
            .pathsToMatch("/{version}/merchants/**")
            .pathsToExclude("/{version}/merchants/*/appointments/**")
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info().title("BlitzPay — Merchant Onboarding API").version("v${apiVersionProperties.versions.merchant}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.merchant)
            }
            .build()

    @Bean
    fun mobileGeofencingApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.mobileGeofencing.label)
            .packagesToScan(MerchantOnboardingController::class.java.packageName)
            .pathsToMatch(
                "/{version}/merchants/*/location",
                "/{version}/merchants/nearby",
                "/{version}/geofence/**",
                "/{version}/proximity",
            )
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info().title("BlitzPay — Mobile Geofencing API").version("v${apiVersionProperties.versions.merchant}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.merchant)
            }
            .build()
}
