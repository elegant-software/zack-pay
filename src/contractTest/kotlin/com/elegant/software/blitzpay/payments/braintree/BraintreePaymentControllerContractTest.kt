package com.elegant.software.blitzpay.payments.braintree

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.api.BraintreeCredentials
import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.payments.braintree.internal.BraintreeCheckoutResult
import com.elegant.software.blitzpay.payments.braintree.internal.BraintreePaymentService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

class BraintreePaymentControllerContractTest : ContractVerifierBase() {

    @MockitoBean
    private lateinit var braintreePaymentService: BraintreePaymentService

    @MockitoBean
    private lateinit var credentialResolver: MerchantCredentialResolver

    private val testMerchantId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val testBranchId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val testCredentials = BraintreeCredentials(
        merchantId = "bt-merchant",
        publicKey = "pub",
        privateKey = "priv",
        environment = "sandbox",
    )

    @BeforeEach
    fun setupResolver() {
        whenever(credentialResolver.resolveBranch(any(), anyOrNull(), anyOrNull())).thenReturn(testBranchId)
        whenever(credentialResolver.resolveBraintree(any(), anyOrNull())).thenReturn(testCredentials)
        whenever(credentialResolver.resolveProductPrice(any())).thenReturn(null)
    }

    @Test
    fun `client-token returns 200 with token`() {
        whenever(braintreePaymentService.generateClientToken(any())).thenReturn(
            "eyJ2ZXJzaW9uIjoy_contract_test_token"
        )

        webTestClient.post().uri("/v1/payments/braintree/client-token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.clientToken").isEqualTo("eyJ2ZXJzaW9uIjoy_contract_test_token")
    }

    @Test
    fun `client-token returns 400 when merchantId missing`() {
        webTestClient.post().uri("/v1/payments/braintree/client-token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `client-token returns 503 when credentials not configured`() {
        whenever(credentialResolver.resolveBraintree(any(), anyOrNull())).thenReturn(null)

        webTestClient.post().uri("/v1/payments/braintree/client-token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `checkout returns success response for valid nonce and amount`() {
        whenever(braintreePaymentService.checkout(any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull())).thenReturn(
            BraintreeCheckoutResult.Success("tx_abc123", "12.50", "EUR", "ORDER-123", null, testMerchantId, testBranchId)
        )

        webTestClient.post().uri("/v1/payments/braintree/checkout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"nonce": "fake-valid-nonce", "amount": 12.50, "currency": "EUR", "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("succeeded")
            .jsonPath("$.paymentRequestId").value<String> { paymentRequestId ->
                require(Regex("[0-9a-fA-F\\-]{36}").matches(paymentRequestId))
            }
            .jsonPath("$.transactionId").isEqualTo("tx_abc123")
            .jsonPath("$.amount").isEqualTo("12.50")
            .jsonPath("$.orderId").isEqualTo("ORDER-123")
            .jsonPath("$.merchantId").isEqualTo(testMerchantId.toString())
    }

    @Test
    fun `checkout echoes invoiceId in success response`() {
        whenever(braintreePaymentService.checkout(any(), any(), any(), any(), any(), anyOrNull(), any(), anyOrNull(), anyOrNull())).thenReturn(
            BraintreeCheckoutResult.Success("tx_inv42", "99.00", "EUR", "ORDER-123", "INV-2026-00042", testMerchantId, testBranchId)
        )

        webTestClient.post().uri("/v1/payments/braintree/checkout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"nonce": "fake-valid-nonce", "amount": 99.00, "orderId": "ORDER-123", "invoiceId": "INV-2026-00042", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("succeeded")
            .jsonPath("$.paymentRequestId").value<String> { paymentRequestId ->
                require(Regex("[0-9a-fA-F\\-]{36}").matches(paymentRequestId))
            }
            .jsonPath("$.orderId").isEqualTo("ORDER-123")
            .jsonPath("$.invoiceId").isEqualTo("INV-2026-00042")
    }

    @Test
    fun `checkout returns 400 when nonce is missing`() {
        webTestClient.post().uri("/v1/payments/braintree/checkout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 12.50, "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `checkout returns 400 when merchantId missing`() {
        webTestClient.post().uri("/v1/payments/braintree/checkout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"nonce": "fake-nonce", "amount": 12.50}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }

    @Test
    fun `checkout returns 503 when credentials not configured`() {
        whenever(credentialResolver.resolveBraintree(any(), anyOrNull())).thenReturn(null)

        webTestClient.post().uri("/v1/payments/braintree/checkout")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"nonce": "fake-valid-nonce", "amount": 12.50, "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isEqualTo(503)
            .expectBody()
            .jsonPath("$.error").exists()
    }
}
