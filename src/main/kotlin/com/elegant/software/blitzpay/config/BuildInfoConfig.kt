package com.elegant.software.blitzpay.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PropertiesLoaderUtils
import java.util.Properties

/**
 * Backs `GET /actuator/info` `build.*`. Gradle's `bootBuildInfo` writes `META-INF/build-info.properties`
 * (Gradle version + build time). The image is built by the shared CI workflow, which passes no build args,
 * so the release tag can't be baked into the jar; instead Helm / ECS set `APP_VERSION` to the deployed image
 * tag and it wins over the Gradle version here. Replaces Boot's own `BuildProperties` (`@ConditionalOnMissingBean`).
 */
@Configuration
class BuildInfoConfig {

    @Bean
    fun buildProperties(@Value("\${APP_VERSION:}") appVersion: String): BuildProperties {
        val entries = Properties()
        val resource = ClassPathResource(BUILD_INFO_LOCATION)
        if (resource.exists()) {
            val source = PropertiesLoaderUtils.loadProperties(resource)
            source.stringPropertyNames()
                .filter { it.startsWith(PREFIX) }
                .forEach { entries[it.removePrefix(PREFIX)] = source.getProperty(it) }
        }
        entries["version"] = appVersion.ifBlank { entries.getProperty("version") ?: "dev" }
        return BuildProperties(entries)
    }

    private companion object {
        const val BUILD_INFO_LOCATION = "META-INF/build-info.properties"
        const val PREFIX = "build."
    }
}
