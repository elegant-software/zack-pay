package com.elegant.software.blitzpay.payments.truelayer.config

import com.truelayer.java.ClientCredentials
import com.truelayer.java.Environment
import com.truelayer.java.SigningOptions
import com.truelayer.java.TrueLayerClient
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.file.Files
import java.nio.file.Path

@Configuration
@EnableConfigurationProperties(TrueLayerProperties::class)
class TrueLayerConfig {

    @Bean
    fun trueLayerSdkClient(trueLayerProperties: TrueLayerProperties): TrueLayerClient {
        val builder = TrueLayerClient.New()

        val env = when (trueLayerProperties.environment.lowercase()) {
            "live" -> Environment.live()
            else -> Environment.sandbox()
        }
        builder.environment(env)

        if (trueLayerProperties.httpLogs || trueLayerProperties.environment.lowercase() != "live") {
            builder.withHttpLogs()
        }

        // Configure client credentials
        builder.clientCredentials(
            ClientCredentials.builder()
                .clientId(trueLayerProperties.clientId)
                .clientSecret(trueLayerProperties.clientSecret)
                .build()
        )

        // Configure signing options using the private key file (filesystem or classpath)
        val privateKeyBytes = run {
            val path = Path.of(trueLayerProperties.privateKeyPath)
            if (Files.exists(path)) {
                Files.readAllBytes(path)
            } else {
                val resourceStream = Thread.currentThread().contextClassLoader
                    .getResourceAsStream(trueLayerProperties.privateKeyPath)
                    ?: this::class.java.classLoader.getResourceAsStream(trueLayerProperties.privateKeyPath)
                    ?: throw IllegalStateException("Private key not found at path '${trueLayerProperties.privateKeyPath}' (filesystem) or on classpath")
                resourceStream.use { it.readAllBytes() }
            }
        }
        builder.signingOptions(
            SigningOptions.builder()
                .keyId(trueLayerProperties.keyId)
                .privateKey(privateKeyBytes)
                .build()
        )

        return builder.build()
    }
}
