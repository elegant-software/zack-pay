package com.elegant.software.blitzpay.config

import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Configuration
import org.springframework.web.accept.ApiVersionResolver
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcVersioningConfig(
    private val apiVersionProperties: ApiVersionProperties,
    private val corsProperties: CorsProperties
) : WebMvcConfigurer {

    override fun configureApiVersioning(configurer: ApiVersionConfigurer) {
        configurer
            .useVersionResolver(PathOnlyApiVersionResolver())
            .setVersionRequired(false)
            .setDefaultVersion(apiVersionProperties.defaultVersion)
            .detectSupportedVersions(true)
    }

    override fun addCorsMappings(registry: CorsRegistry) {
        // First matching mapping wins, so the kitchen mapping must be registered before the catch-all.
        registry.addMapping(KITCHEN_PATH_PATTERN)
            .allowedOrigins(*(corsProperties.allowedOrigins + corsProperties.kitchenAllowedOrigins).toTypedArray())
            .allowedMethods(*corsProperties.allowedMethods.toTypedArray())
            .allowedHeaders(*corsProperties.allowedHeaders.toTypedArray())
        registry.addMapping("/**")
            .allowedOrigins(*corsProperties.allowedOrigins.toTypedArray())
            .allowedMethods(*corsProperties.allowedMethods.toTypedArray())
            .allowedHeaders(*corsProperties.allowedHeaders.toTypedArray())
    }

    private companion object {
        const val KITCHEN_PATH_PATTERN = "/*/merchant/branches/*/kitchen/**"
    }
}

class PathOnlyApiVersionResolver : ApiVersionResolver {
    private val versionPattern = Regex("^v(\\d+(?:\\.\\d+)*)$")

    override fun resolveVersion(request: HttpServletRequest): String? {
        val path = request.requestURI

        if (path.startsWith("/mcp")) return null

        val firstSegment = path.trimStart('/').substringBefore('/')
        val match = versionPattern.matchEntire(firstSegment) ?: return null
        return match.groupValues[1]
    }
}
