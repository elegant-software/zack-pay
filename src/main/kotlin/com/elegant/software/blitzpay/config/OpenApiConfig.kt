package com.elegant.software.blitzpay.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.security.OAuthFlow
import io.swagger.v3.oas.models.security.OAuthFlows
import io.swagger.v3.oas.models.security.Scopes
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType

@Configuration
class OpenApiConfig(
    private val apiVersionProperties: ApiVersionProperties,
    private val openApiGroupProperties: OpenApiGroupProperties,
    @Value("\${keycloak.iam.server-url}") private val keycloakServerUrl: String,
    @Value("\${keycloak.iam.realm}") private val keycloakRealm: String,
) {

    @Bean
    fun openApi(): OpenAPI {
        val baseUrl = "$keycloakServerUrl/realms/$keycloakRealm/protocol/openid-connect"
        return OpenAPI()
            .components(
                Components()
                    .addSecuritySchemes(
                        "bearerAuth",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("Paste a Keycloak access token directly (no 'Bearer ' prefix)")
                    )
                    .addSecuritySchemes(
                        "oauth2",
                        SecurityScheme()
                            .type(SecurityScheme.Type.OAUTH2)
                            .flows(
                                OAuthFlows().authorizationCode(
                                    OAuthFlow()
                                        .authorizationUrl("$baseUrl/auth")
                                        .tokenUrl("$baseUrl/token")
                                        .scopes(
                                            Scopes()
                                                .addString("openid", "OpenID Connect")
                                                .addString("profile", "User profile")
                                        )
                                )
                            )
                    )
            )
            // OR: satisfying either scheme unlocks the padlock on every operation
            .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
            .addSecurityItem(SecurityRequirement().addList("oauth2"))
    }

    @Bean
    fun paymentsGroup(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.general.label)
            .pathsToMatch("/{version}/payments/**")
            .addOpenApiCustomizer { openApi ->
                openApi.paths = rewriteVersionPaths(openApi.paths, apiVersionProperties.versions.payments)
            }
            .build()

    @Bean
    fun actuatorGroup(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group(openApiGroupProperties.groups.actuator.label)
            .pathsToMatch("/actuator/**")
            .build()

    /**
     * Replaces inline `description` properties in multipart/form-data schemas with a $ref.
     * The property name "description" shadows the OpenAPI Schema Object keyword of the same
     * name, causing IntelliJ's validator to misfire. A $ref is unambiguous to the validator.
     */
    @Bean
    fun multipartDescriptionFieldCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        var refAdded = false
        openApi.paths?.values?.forEach { pathItem ->
            pathItem.readOperations().forEach { operation ->
                val props = operation.requestBody
                    ?.content
                    ?.get(MediaType.MULTIPART_FORM_DATA_VALUE)
                    ?.schema
                    ?.properties
                    ?: return@forEach
                if (!props.containsKey("description")) return@forEach
                if (!refAdded) {
                    if (openApi.components == null) openApi.components = Components()
                    openApi.components.addSchemas(
                        "FormDescription",
                        Schema<String>().type("string")
                    )
                    refAdded = true
                }
                @Suppress("UNCHECKED_CAST")
                props["description"] = Schema<Any>().`$ref`("#/components/schemas/FormDescription")
            }
        }
    }
}
