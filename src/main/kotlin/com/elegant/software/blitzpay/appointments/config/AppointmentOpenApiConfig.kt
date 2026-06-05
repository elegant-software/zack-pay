package com.elegant.software.blitzpay.appointments.config

import com.elegant.software.blitzpay.appointments.web.ShopperAppointmentController
import com.elegant.software.blitzpay.config.ApiVersionProperties
import com.elegant.software.blitzpay.config.OpenApiGroupProperties
import com.elegant.software.blitzpay.config.rewriteVersionPaths
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AppointmentOpenApiConfig(
    private val apiVersionProperties: ApiVersionProperties,
    private val openApiGroupProperties: OpenApiGroupProperties,
) {

    @Bean
    fun appointmentApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.appointment.label)
            .packagesToScan(ShopperAppointmentController::class.java.packageName)
            .pathsToMatch(
                "/{version}/shopper/appointments/**",
                "/{version}/merchants/*/appointments/**",
            )
            .addOpenApiCustomizer { openApi ->
                openApi.info = Info()
                    .title(openApiGroupProperties.groups.appointment.label)
                    .version("v${apiVersionProperties.versions.appointment}")
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.appointment)
            }
            .build()
}
