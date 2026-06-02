package com.elegant.software.blitzpay.merchant.iam.config

import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakBearerTokenInterceptor
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakGroupClient
import com.elegant.software.blitzpay.merchant.iam.outbound.KeycloakTokenProvider
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory

@Configuration
@Profile("!contract-test")
@EnableConfigurationProperties(KeycloakProperties::class)
class KeycloakWebClientConfig {

    @Bean
    fun keycloakGroupClient(
        properties: KeycloakProperties,
        tokenProvider: KeycloakTokenProvider,
    ): KeycloakGroupClient {
        val restClient = RestClient.builder()
            .baseUrl("${properties.serverUrl}/admin/realms/${properties.realm}")
            .requestInterceptor(KeycloakBearerTokenInterceptor(tokenProvider))
            .build()
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(KeycloakGroupClient::class.java)
    }
}
