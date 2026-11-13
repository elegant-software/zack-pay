package com.elegant.software.blitzpay.order

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.order.api.OrderItemResponse
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.OrderOrigin
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.service.GuestOrderCreated
import com.elegant.software.blitzpay.order.service.OrderService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.util.Base64
import java.util.UUID

/** Storefront guest order endpoint called with and without a Kiosk Login token (ADR 0001). */
class KioskOrderContractTest : ContractVerifierBase() {
    @MockitoBean
    private lateinit var orderService: OrderService

    private val merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val branchId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val body = """{"branchId":"$branchId","items":[{"productId":"22222222-2222-2222-2222-222222222221","quantity":1}]}"""

    private fun bearer(claims: String): String =
        "Bearer fakeheader.${Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray())}.fakesig"

    private fun order(origin: OrderOrigin) = OrderResponse(
        orderId = "ORDER-123", merchantId = merchantId, branchId = branchId, status = OrderStatus.CREATED,
        creatorType = CreatorType.GUEST, createdById = "guest", currency = "EUR", totalAmountMinor = 1099,
        paymentRetryAllowed = true,
        items = listOf(OrderItemResponse(UUID.randomUUID(), UUID.randomUUID(), "Coffee", 1, 1099, 1099)),
        createdAt = Instant.parse("2026-11-03T10:00:00Z"), origin = origin,
    )

    @Test
    fun `a kiosk login token reaches the order service with its branch and the order is KIOSK`() {
        whenever(orderService.createGuestOrder(any(), eq(branchId))).thenReturn(GuestOrderCreated(order(OrderOrigin.KIOSK), "tok"))

        webTestClient.post().uri("/v1/storefront/orders")
            .header(HttpHeaders.AUTHORIZATION, bearer("""{"sub":"kiosk","azp":"blitzpay-kiosk","groups":["/merchants/merchant_x/branch_$branchId"]}"""))
            .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
            .exchange()
            .expectStatus().isCreated
            .expectBody().jsonPath("$.origin").isEqualTo("KIOSK")
    }

    @Test
    fun `without a kiosk token the order service sees no kiosk and the order is STANDARD`() {
        whenever(orderService.createGuestOrder(any(), isNull())).thenReturn(GuestOrderCreated(order(OrderOrigin.STANDARD), "tok"))

        val anonymous = webTestClient.mutate().defaultHeaders { it.remove("Authorization") }.build()
        anonymous.post().uri("/v1/storefront/orders").contentType(MediaType.APPLICATION_JSON).bodyValue(body)
            .exchange()
            .expectStatus().isCreated
            .expectBody().jsonPath("$.origin").isEqualTo("STANDARD")

        // A Kitchen Login of the same branch is no Kiosk either
        webTestClient.post().uri("/v1/storefront/orders")
            .header(HttpHeaders.AUTHORIZATION, bearer("""{"sub":"kitchen","azp":"blitzpay-kitchen","groups":["branch_$branchId"]}"""))
            .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
            .exchange().expectStatus().isCreated
        verify(orderService, never()).createGuestOrder(any(), org.mockito.kotlin.argThat { this != null })
    }

    @Test
    fun `a kiosk token without exactly one branch is 403 and never creates an order`() {
        listOf(
            """{"sub":"kiosk","azp":"blitzpay-kiosk"}""",
            """{"sub":"kiosk","azp":"blitzpay-kiosk","groups":["branch_$branchId","branch_${UUID.randomUUID()}"]}""",
        ).forEach { claims ->
            webTestClient.post().uri("/v1/storefront/orders").header(HttpHeaders.AUTHORIZATION, bearer(claims))
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange()
                .expectStatus().isForbidden
        }
        verify(orderService, never()).createGuestOrder(any(), anyOrNull())
    }
}
