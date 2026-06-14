package com.elegant.software.blitzpay.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient

/**
 * Proxies Keycloak's RFC 8414 Authorization Server Metadata so that MCP clients
 * following the MCP OAuth2 discovery protocol can find the real authorization
 * server (Keycloak) from /.well-known/oauth-authorization-server on this host.
 *
 * Without this, MCP clients that don't find `authorization_servers` in the
 * protected resource metadata fall back to this host for AS discovery and have
 * no path to obtain a token.
 */
@RestController
class OAuthAuthorizationServerMetadataController(
    restClientBuilder: RestClient.Builder,
    @Value("\${keycloak.iam.server-url}") private val keycloakServerUrl: String,
    @Value("\${keycloak.iam.realm}") private val keycloakRealm: String,
) {
    private val restClient = restClientBuilder.build()

    @GetMapping("/.well-known/oauth-authorization-server", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun metadata(): ResponseEntity<String> {
        val upstreamUrl = "$keycloakServerUrl/realms/$keycloakRealm/.well-known/oauth-authorization-server"
        return try {
            val body = restClient.get().uri(upstreamUrl).retrieve().body(String::class.java)
            ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)
        } catch (ex: Exception) {
            ResponseEntity.status(503).build()
        }
    }
}
