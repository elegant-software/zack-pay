package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.api.BranchTerminalLocation
import com.elegant.software.blitzpay.merchant.api.MerchantAccessGateway
import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.order.api.OrderPaymentAttempt
import com.elegant.software.blitzpay.payments.stripe.outbound.StripeCheckoutSessionResult
import com.elegant.software.blitzpay.payments.stripe.outbound.StripeIntentResult
import com.elegant.software.blitzpay.payments.stripe.outbound.StripePaymentService
import com.stripe.model.PaymentIntent
import com.stripe.model.terminal.Reader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

class StripePaymentControllerContractTest : ContractVerifierBase() {

    @MockitoBean
    private lateinit var stripePaymentService: StripePaymentService

    @MockitoBean
    private lateinit var credentialResolver: MerchantCredentialResolver

    @MockitoBean
    private lateinit var merchantAccess: MerchantAccessGateway

    private val testMerchantId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val testBranchId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @BeforeEach
    fun setupResolver() {
        whenever(credentialResolver.resolveBranch(any(), anyOrNull(), anyOrNull())).thenReturn(testBranchId)
        whenever(credentialResolver.resolveProductPrice(any())).thenReturn(null)
        whenever(merchantAccess.isManagerOrPlatformAdmin(any(), anyOrNull())).thenReturn(true)
        whenever(merchantAccess.worksAtBranch(any(), any(), anyOrNull())).thenReturn(true)
    }

    @Test
    fun `returns 200 with clientSecret alias and publishableKey for valid amount`() {
        whenever(stripePaymentService.createIntent(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any())).thenReturn(
            StripeIntentResult("pi_test_secret_abc", "pi_abc123", "acct_test_contract")
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
            StripeIntentResult("pi_test_secret_xyz", "pi_xyz789", "acct_test_contract")
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
            .jsonPath("$.detail").exists()
    }

    @Test
    fun `returns 400 when amount is zero`() {
        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": 0, "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.detail").exists()
    }

    @Test
    fun `returns 400 when amount is negative`() {
        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"amount": -1.0, "orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId"}""")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.detail").exists()
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
            .jsonPath("$.detail").exists()
    }

    @Test
    fun `returns 200 with checkout session url for pay by bank`() {
        whenever(
            stripePaymentService.createPayByBankCheckoutSession(
                any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any()
            )
        ).thenReturn(
            StripeCheckoutSessionResult(
                checkoutUrl = "https://checkout.stripe.com/pay/cs_test_123",
                checkoutSessionId = "cs_test_123",
                connectedAccountId = "acct_test_contract"
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

    @Test
    fun `card_present returns the open terminal PaymentIntent on retry`() {
        val paymentRequestId = UUID.fromString("00000000-0000-0000-0000-0000000000aa")
        whenever(stripeConnectGateway.isTapToPayEnabled(any())).thenReturn(true)
        whenever(orderGateway.findLastPaymentAttempt("ORDER-123")).thenReturn(OrderPaymentAttempt(paymentRequestId, "STRIPE_TERMINAL", "pi_open"))
        whenever(stripePaymentService.retrieveIntent("pi_open", "acct_test_contract")).thenReturn(
            PaymentIntent().apply { id = "pi_open"; status = "requires_payment_method"; clientSecret = "pi_open_secret"; amount = 1099L; currency = "eur" }
        )

        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId", "paymentMethodType": "card_present"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentRequestId").isEqualTo(paymentRequestId.toString())
            .jsonPath("$.clientSecret").isEqualTo("pi_open_secret")
            .jsonPath("$.paymentIntent").isEqualTo("pi_open")
    }

    @Test
    fun `card_present returns 409 ORDER_ALREADY_PAID when the terminal PaymentIntent succeeded`() {
        whenever(stripeConnectGateway.isTapToPayEnabled(any())).thenReturn(true)
        whenever(orderGateway.findLastPaymentAttempt("ORDER-123")).thenReturn(OrderPaymentAttempt(UUID.randomUUID(), "STRIPE_TERMINAL", "pi_done"))
        whenever(stripePaymentService.retrieveIntent("pi_done", "acct_test_contract")).thenReturn(
            PaymentIntent().apply { id = "pi_done"; status = "succeeded"; amount = 1099L; currency = "eur" }
        )

        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId", "paymentMethodType": "card_present"}""")
            .exchange()
            .expectStatus().isEqualTo(409)
            .expectBody()
            .jsonPath("$.code").isEqualTo("ORDER_ALREADY_PAID")
    }

    @Test
    fun `card_present returns 403 when terminal payments are not enabled`() {
        whenever(stripeConnectGateway.isTapToPayEnabled(any())).thenReturn(false)

        webTestClient.post().uri("/v1/payments/stripe/create-intent")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"orderId": "ORDER-123", "merchantId": "$testMerchantId", "branchId": "$testBranchId", "paymentMethodType": "card_present"}""")
            .exchange()
            .expectStatus().isForbidden
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Terminal payments are not enabled for this merchant")
    }

    @Test
    fun `lists terminal readers of the merchant's branch locations`() {
        whenever(stripeConnectGateway.isTapToPayEnabled(any())).thenReturn(true)
        whenever(stripeConnectGateway.findTerminalLocations(testMerchantId)).thenReturn(
            listOf(BranchTerminalLocation(testBranchId, "Hauptfiliale", "tml_1"))
        )
        whenever(stripePaymentService.listReaders(eq("tml_1"), eq("acct_test_contract"))).thenReturn(
            listOf(Reader().apply {
                id = "tmr_1"; label = "WisePad 3 Theke"; deviceType = "bbpos_wisepad3"; serialNumber = "WSC5123"
                deviceSwVersion = "4.01.07.00"; status = "online"; lastSeenAt = 1_794_219_300_000L
            })
        )

        webTestClient.get().uri("/v1/payments/stripe/terminal/readers?merchantId=$testMerchantId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].id").isEqualTo("tmr_1")
            .jsonPath("$[0].branchId").isEqualTo(testBranchId.toString())
            .jsonPath("$[0].branchName").isEqualTo("Hauptfiliale")
            .jsonPath("$[0].label").isEqualTo("WisePad 3 Theke")
            .jsonPath("$[0].deviceType").isEqualTo("bbpos_wisepad3")
            .jsonPath("$[0].serialNumber").isEqualTo("WSC5123")
            .jsonPath("$[0].softwareVersion").isEqualTo("4.01.07.00")
            .jsonPath("$[0].status").isEqualTo("online")
            .jsonPath("$[0].lastSeenAt").isEqualTo("2026-11-09T10:15:00Z")
    }

    @Test
    fun `lists no terminal readers when terminal payments are not enabled`() {
        whenever(stripeConnectGateway.isTapToPayEnabled(any())).thenReturn(false)

        webTestClient.get().uri("/v1/payments/stripe/terminal/readers?merchantId=$testMerchantId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .json("[]")
    }

    @Test
    fun `refuses terminal readers to callers who may not manage the merchant`() {
        whenever(merchantAccess.isManagerOrPlatformAdmin(any(), anyOrNull())).thenReturn(false)

        webTestClient.get().uri("/v1/payments/stripe/terminal/readers?merchantId=$testMerchantId")
            .exchange()
            .expectStatus().isForbidden
    }
}
