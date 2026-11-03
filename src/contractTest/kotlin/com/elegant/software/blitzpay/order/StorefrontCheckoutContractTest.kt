package com.elegant.software.blitzpay.order

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.order.api.OrderItemResponse
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.PreparationStatus
import com.elegant.software.blitzpay.order.service.GuestOrderCreated
import com.elegant.software.blitzpay.order.service.OrderService
import com.elegant.software.blitzpay.order.service.StorefrontOrderBus
import com.elegant.software.blitzpay.payments.stripe.outbound.StripeCheckoutSessionResult
import com.elegant.software.blitzpay.payments.stripe.outbound.StripeIntentResult
import com.elegant.software.blitzpay.payments.stripe.outbound.StripePaymentService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import reactor.test.StepVerifier
import java.time.Duration
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.UUID

/** Web storefront guest checkout: public endpoints, authorised only by the guest_token session cookie. */
class StorefrontCheckoutContractTest : ContractVerifierBase() {
    @MockitoBean
    private lateinit var orderService: OrderService

    @MockitoBean
    private lateinit var stripePaymentService: StripePaymentService

    @MockitoBean
    private lateinit var credentialResolver: MerchantCredentialResolver

    @Autowired
    private lateinit var storefrontOrderBus: StorefrontOrderBus

    private lateinit var anonymous: WebTestClient

    private val branchId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val order = OrderResponse(
        orderId = "ORDER-123",
        merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        branchId = branchId,
        status = OrderStatus.CREATED,
        creatorType = CreatorType.GUEST,
        createdById = "guest",
        currency = "EUR",
        totalAmountMinor = 1099,
        paymentRetryAllowed = true,
        items = listOf(OrderItemResponse(UUID.randomUUID(), UUID.randomUUID(), "Coffee", 1, 1099, 1099)),
        createdAt = Instant.parse("2026-11-03T10:00:00Z"),
        pickupNumber = 7,
    )

    @BeforeEach
    fun anonymousClient() {
        anonymous = webTestClient.mutate().defaultHeaders { it.remove("Authorization") }.build()
        whenever(credentialResolver.resolveBranch(any(), anyOrNull(), anyOrNull())).thenReturn(branchId)
    }

    @Test
    fun `create guest order sets an HttpOnly session cookie and keeps the token out of the body`() {
        whenever(orderService.createGuestOrder(any())).thenReturn(GuestOrderCreated(order, "tok-secret"))

        anonymous.post().uri("/v1/storefront/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"branchId": "$branchId", "items": [{"productId": "22222222-2222-2222-2222-222222222221", "quantity": 1}]}""")
            .exchange()
            .expectStatus().isCreated
            .expectHeader().value("Set-Cookie") { cookie ->
                require(cookie.startsWith("guest_token=tok-secret")) { cookie }
                listOf("HttpOnly", "Secure", "SameSite=Lax", "Path=/v1/storefront").forEach { require(it in cookie) { "$it missing: $cookie" } }
                require("Max-Age" !in cookie && "Expires" !in cookie) { "must be a session cookie: $cookie" }
            }
            .expectBody()
            .jsonPath("$.orderId").isEqualTo("ORDER-123")
            .jsonPath("$.creatorType").isEqualTo("GUEST")
            .jsonPath("$.totalAmountMinor").isEqualTo(1099)
            .jsonPath("$.token").doesNotExist()
    }

    @Test
    fun `get guest order requires the cookie`() {
        whenever(orderService.getGuestOrder("ORDER-123", "tok-secret")).thenReturn(order)
        whenever(orderService.getGuestOrder("ORDER-123", null))
            .thenThrow(NoSuchElementException("Order not found: ORDER-123"))

        anonymous.get().uri("/v1/storefront/orders/ORDER-123").cookie("guest_token", "tok-secret")
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.status").isEqualTo("CREATED")
        anonymous.get().uri("/v1/storefront/orders/ORDER-123")
            .exchange().expectStatus().isNotFound
    }

    @Test
    fun `order events stream requires the cookie before opening`() {
        whenever(orderService.getGuestOrder("ORDER-123", null))
            .thenThrow(NoSuchElementException("Order not found: ORDER-123"))

        anonymous.get().uri("/v1/storefront/orders/ORDER-123/events").accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isNotFound
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
    }

    @Test
    fun `order events stream sends the current order then every change as order changed`() {
        whenever(orderService.getGuestOrder("ORDER-123", "tok-secret")).thenReturn(order)

        val events = anonymous.get().uri("/v1/storefront/orders/ORDER-123/events").cookie("guest_token", "tok-secret")
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectHeader().valueEquals("X-Accel-Buffering", "no")
            .returnResult(object : ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .responseBody
            .filter { it.event() != null }

        StepVerifier.create(events)
            .assertNext { sse ->
                assert(sse.event() == "order.changed") { "unexpected event ${sse.event()}" }
                assert(sse.id() == "ORDER-123") { "unexpected id ${sse.id()}" }
                assert(sse.data()!!.contains("\"preparationStatus\":\"RECEIVED\"")) { sse.data()!! }
                assert(sse.data()!!.contains("\"pickupNumber\":7")) { sse.data()!! }
            }
            .then { storefrontOrderBus.emit(order.copy(status = OrderStatus.PAID, preparationStatus = PreparationStatus.READY)) }
            .assertNext { sse ->
                assert(sse.event() == "order.changed")
                assert(sse.data()!!.contains("\"preparationStatus\":\"READY\"")) { sse.data()!! }
                assert(sse.data()!!.contains("\"status\":\"PAID\"")) { sse.data()!! }
            }
            .thenCancel()
            .verify(Duration.ofSeconds(10))
    }

    @Test
    fun `payment without a valid guest token is 404`() {
        whenever(orderGateway.isGuestTokenValid(any(), anyOrNull())).thenReturn(false)

        anonymous.post().uri("/v1/storefront/orders/ORDER-123/payment").cookie("guest_token", "stolen")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"method": "CARD"}""")
            .exchange().expectStatus().isNotFound
    }

    @Test
    fun `card payment returns Payment Element credentials`() {
        whenever(orderGateway.isGuestTokenValid("ORDER-123", "tok-secret")).thenReturn(true)
        whenever(stripePaymentService.createIntent(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any()))
            .thenReturn(StripeIntentResult("pi_secret", "pi_1", "acct_test_contract"))

        anonymous.post().uri("/v1/storefront/orders/ORDER-123/payment").cookie("guest_token", "tok-secret")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"method": "CARD"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.method").isEqualTo("CARD")
            .jsonPath("$.clientSecret").isEqualTo("pi_secret")
            .jsonPath("$.publishableKey").isEqualTo("pk_test_contract_test_dummy")
            .jsonPath("$.connectedAccountId").isEqualTo("acct_test_contract")
            .jsonPath("$.checkoutUrl").doesNotExist()
    }

    @Test
    fun `pay by bank payment returns the hosted checkout url`() {
        whenever(orderGateway.isGuestTokenValid("ORDER-123", "tok-secret")).thenReturn(true)
        whenever(stripePaymentService.createPayByBankCheckoutSession(any(), any(), any(), any(), anyOrNull(), any(), any(), anyOrNull(), any(), any(), any()))
            .thenReturn(StripeCheckoutSessionResult("https://checkout.stripe.com/c/pay/cs_1", "cs_1", "acct_test_contract"))

        anonymous.post().uri("/v1/storefront/orders/ORDER-123/payment").cookie("guest_token", "tok-secret")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"method": "PAY_BY_BANK"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.method").isEqualTo("PAY_BY_BANK")
            .jsonPath("$.checkoutUrl").isEqualTo("https://checkout.stripe.com/c/pay/cs_1")
            .jsonPath("$.clientSecret").doesNotExist()
    }
}
