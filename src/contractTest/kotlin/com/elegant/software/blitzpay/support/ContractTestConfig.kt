package com.elegant.software.blitzpay.support

import com.elegant.software.blitzpay.appointments.api.AppointmentQueryService
import com.elegant.software.blitzpay.order.api.PaymentSettlementGateway
import com.elegant.software.blitzpay.payments.push.config.ExpoPushProperties
import com.elegant.software.blitzpay.payments.push.service.ExpoMessage
import com.elegant.software.blitzpay.payments.push.service.ExpoPushClient
import com.elegant.software.blitzpay.payments.push.service.ExpoTicket
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.metamodel.Metamodel
import org.jooq.DSLContext
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant
import java.util.Base64

@TestConfiguration
@Profile("contract-test")
class ContractTestConfig {

    @Bean
    @Primary
    fun mockExpoPushClient(properties: ExpoPushProperties, expoWebClient: WebClient): ExpoPushClient =
        object : ExpoPushClient(properties, expoWebClient) {
            override fun send(messages: List<ExpoMessage>): List<ExpoTicket> =
                messages.map { ExpoTicket(it.to, "contract-ticket-${it.to.hashCode()}", ExpoTicket.Status.OK, null) }
        }

    @Bean
    @Primary
    fun mockDslContext(): DSLContext = mock()

    @Bean
    @Primary
    fun mockAppointmentQueryService(): AppointmentQueryService = mock()

    @Bean
    @Primary
    fun mockPaymentSettlementGateway(): PaymentSettlementGateway = mock()

    @Bean(name = ["entityManagerFactory"])
    @Primary
    fun mockEntityManagerFactory(): EntityManagerFactory {
        val metamodel = mock<Metamodel> {
            on { entities } doReturn emptySet()
            on { managedTypes } doReturn emptySet()
            on { embeddables } doReturn emptySet()
        }
        return mock { on { getMetamodel() } doReturn metamodel }
    }

    // Accepts any bearer token so contract tests don't need a live Keycloak server.
    // A `groups` claim in the (unsigned) payload is passed through for branch-scoped endpoints,
    // and `realm_access.roles` for endpoints that also admit a platform admin.
    @Bean
    @Primary
    fun mockJwtDecoder(): JwtDecoder = JwtDecoder { token ->
        Jwt.withTokenValue(token)
            .header("alg", "none")
            .subject("contract-test-user")
            .issuer("https://contract-test.local/realms/blitzpay")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .apply { groupsClaim(token)?.let { claim("groups", it) } }
            .apply { realmRolesClaim(token)?.let { claim("realm_access", mapOf("roles" to it)) } }
            .build()
    }

    private fun groupsClaim(token: String): List<String>? = stringArray(token, "\"groups\"\\s*:\\s*\\[([^\\]]*)]")

    private fun realmRolesClaim(token: String): List<String>? =
        stringArray(token, "\"realm_access\"\\s*:\\s*\\{\\s*\"roles\"\\s*:\\s*\\[([^\\]]*)]")

    private fun stringArray(token: String, pattern: String): List<String>? {
        val payload = token.split('.').getOrNull(1) ?: return null
        val json = runCatching { String(Base64.getUrlDecoder().decode(payload)) }.getOrNull() ?: return null
        val array = Regex(pattern).find(json)?.groupValues?.get(1) ?: return null
        return Regex("\"([^\"]*)\"").findAll(array).map { it.groupValues[1] }.toList()
    }
}
