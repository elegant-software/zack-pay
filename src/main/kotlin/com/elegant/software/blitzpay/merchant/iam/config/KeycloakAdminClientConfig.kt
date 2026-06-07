package com.elegant.software.blitzpay.merchant.iam.config

import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!contract-test")
class KeycloakAdminClientConfig {

    @Bean
    fun keycloakAdminClient(properties: KeycloakProperties): Keycloak =
        KeycloakBuilder.builder()
            .serverUrl(properties.serverUrl)
            .realm(properties.realm)
            .clientId(properties.adminClientId)
            .clientSecret(properties.adminClientSecret)
            .grantType("client_credentials")
            .build()
}
