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
    @Bean
    @Primary
    fun mockJwtDecoder(): JwtDecoder = JwtDecoder { token ->
        Jwt.withTokenValue(token)
            .header("alg", "none")
            .subject("contract-test-user")
            .issuer("https://contract-test.local/realms/blitzpay")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()
    }
}
