package com.elegant.software.blitzpay.merchant.iam.config

import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!contract-test")
@EnableConfigurationProperties(KeycloakProperties::class, KitchenAccessProperties::class, KioskAccessProperties::class)
class KeycloakAdminClientConfig {

    /** Admin client for the business realm (merchant users, groups, Kitchen and Kiosk Logins). */
    @Bean
    @Primary
    fun keycloakAdminClient(properties: KeycloakProperties): Keycloak = build(properties, properties.realm)

    /** Admin client for the customer realm (shopper signup, shopper account export and deletion). */
    @Bean(CUSTOMER_KEYCLOAK)
    fun customerKeycloakAdminClient(properties: KeycloakProperties): Keycloak = build(properties, properties.customerRealm)

    private fun build(properties: KeycloakProperties, realm: String): Keycloak =
        KeycloakBuilder.builder()
            .serverUrl(properties.serverUrl)
            .realm(realm)
            .clientId(properties.adminClientId)
            .clientSecret(properties.adminClientSecret)
            .grantType("client_credentials")
            .build()

    companion object {
        const val CUSTOMER_KEYCLOAK = "customerKeycloak"
    }
}
