package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.api.BranchCreated
import com.elegant.software.blitzpay.merchant.api.MerchantActivated
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.boot.test.context.TestConfiguration
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Import(MerchantIamContractTest.TestConfig::class)
class MerchantIamContractTest : ContractVerifierBase() {

    @Autowired
    private lateinit var publishedEventSink: PublishedEventSink

    @Test
    fun `merchant registration publishes MerchantActivated event`() {
        whenever(merchantRepository.save(any<Merchant>())).thenAnswer { it.arguments[0] }
        publishedEventSink.clear()

        webTestClient.post()
            .uri("/v1/merchants")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "merchantName": "IAM Test GmbH",
                  "businessType": "RETAIL",
                  "operatingCountry": "DE"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.merchantStatus").isEqualTo(MerchantOperationalStatus.ACTIVE.name)

        val event = publishedEventSink.first(MerchantActivated::class.java)
        assertNotNull(event)
        assertEquals("IAM Test GmbH", event.merchantName)
    }

    @Test
    fun `branch creation publishes BranchCreated event`() {
        val merchantId = UUID.randomUUID()
        val merchant = Merchant(
            id = merchantId,
            merchantName = "Branch IAM GmbH",
            businessType = "RETAIL",
            operatingCountry = "DE",
            merchantStatus = MerchantOperationalStatus.ACTIVE,
        )
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(merchantRepository.findById(merchantId)).thenReturn(java.util.Optional.of(merchant))
        whenever(merchantBranchRepository.save(any<MerchantBranch>())).thenAnswer { it.arguments[0] }
        publishedEventSink.clear()

        webTestClient.post()
            .uri("/v1/merchants/$merchantId/branches")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "IAM Branch",
                  "addressLine1": "IAM Branch Street 1",
                  "city": "Berlin",
                  "postalCode": "10115",
                  "country": "DE",
                  "latitude": 52.52,
                  "longitude": 13.405,
                  "geofenceRadiusMeters": 150
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo("IAM Branch")

        val event = publishedEventSink.first(BranchCreated::class.java)
        assertNotNull(event)
        assertEquals(merchantId, event.merchantId)
        assertEquals("IAM Branch", event.branchName)
        assertEquals("Branch IAM GmbH", event.merchantName)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class TestConfig {

        @Bean
        fun publishedEventSink() = PublishedEventSink()

        @Bean
        @Primary
        fun recordingApplicationEventPublisher(
            applicationContext: ApplicationContext,
            sink: PublishedEventSink,
        ): ApplicationEventPublisher = ApplicationEventPublisher { event ->
            sink.record(event)
            applicationContext.publishEvent(event)
        }
    }

    open class PublishedEventSink {
        private val events: CopyOnWriteArrayList<Any> by lazy { CopyOnWriteArrayList() }

        open fun record(event: Any) {
            events += event
        }

        open fun <T : Any> first(type: Class<T>): T? = events.firstNotNullOfOrNull { event ->
            if (type.isInstance(event)) type.cast(event) else null
        }

        open fun clear() {
            events.clear()
        }
    }
}
