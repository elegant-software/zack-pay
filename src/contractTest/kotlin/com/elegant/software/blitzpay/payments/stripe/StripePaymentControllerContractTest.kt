package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.payments.stripe.internal.StripeCheckoutSessionResult
import com.elegant.software.blitzpay.payments.stripe.internal.StripeIntentResult
import com.elegant.software.blitzpay.payments.stripe.internal.StripePaymentService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.core.publisher.Mono
import java.util.UUID

class StripePaymentControllerContractTest : ContractVerifierBase() {

    @MockitoBean
    private lateinit var stripePaymentService: StripePaymentService

    @MockitoBean
    private lateinit var credentialResolver: MerchantCredentialResolver

    private val testMerchantId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val testBranchId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @BeforeEach
    fun setupResolver() {
        whenever(credentialResolver.resolveBranch(any(), anyOrNull(), anyOrNull())).thenReturn(testBranchId)
        whenever(credentialResolver.resolveProductPrice(any())).thenReturn(null)
    }

    @Test
    fun `returns 200 with clientSecret alias and publishableKey for valid amount`() {
        whenever(stripePaymentService.createIntent(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any())).thenReturn(
            Mono.just(StripeIntentResult("pi_test_secret_abc", "pi_abc123", "acct_test_contract"))
        )

        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 12.50, "currency": "eur", "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentRequestId").value<String> { paymentRequestId ->
                require(Regex("[0-9a-fA-F\\-]{36}").matches(paymentRequestId))
            }
            .jsonPath("$.clientSecret").isEqualTo("pi_test_secret_abc")
            .jsonPath("$.paymentIntent").isEqualTo("pi_abc123")
            .jsonPath("$.publishableKey").isEqualTo("pk_test_contract_test_dummy")
            .jsonPath("$.connectedAccountId").isEqualTo("acct_test_contract")
    }

    @Test
    fun `returns 200 with default currency when currency omitted`() {
        whenever(stripePaymentService.createIntent(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any())).thenReturn(
            Mono.just(StripeIntentResult("pi_test_secret_xyz", "pi_xyz789", "acct_test_contract"))
        )

        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 5.00, "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentRequestId").value<String> { paymentRequestId ->
                require(Regex("[0-9a-fA-F\\-]{36}").matches(paymentRequestId))
            }
            .jsonPath("$.clientSecret").exists()
            .jsonPath("$.paymentIntent").exists()
    }

    @Test
    fun `returns 400 when merchantId is missing`() {
        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 12.50, "currency": "eur"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `returns 400 when amount is zero`() {
        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 0, "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `returns 400 when amount is negative`() {
        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": -1.0, "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `returns 503 when stripe connect account not configured`() {
        whenever(stripeConnectGateway.findConnectedAccountId(any())).thenReturn(null)

        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 12.50, "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `returns 200 with checkout session url for pay by bank`() {
        whenever(
            stripePaymentService.createPayByBankCheckoutSession(
                any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any()
            )
        ).thenReturn(
            Mono.just(
                StripeCheckoutSessionResult(
                    checkoutUrl = "https://checkout.stripe.com/pay/cs_test_123",
                    checkoutSessionId = "cs_test_123",
                    connectedAccountId = "acct_test_contract"
                )
            )
        )

        webTestClient.post().uri("/v1/payments/stripe/pay-by-bank/create-checkout-session")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """{"orderId":"ORDER-123","merchantId":"$testMerchantId","branchId":"$testBranchId","successUrl":"blitzpay://payment/success","cancelUrl":"blitzpay://payment/cancel"}"""
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentRequestId").value<String> { paymentRequestId ->
                require(Regex("[0-9a-fA-F\\-]{36}").matches(paymentRequestId))
            }
            .jsonPath("$.checkoutUrl").isEqualTo("https://checkout.stripe.com/pay/cs_test_123")
            .jsonPath("$.checkoutSessionId").isEqualTo("cs_test_123")
            .jsonPath("$.connectedAccountId").isEqualTo("acct_test_contract")
    }
}
