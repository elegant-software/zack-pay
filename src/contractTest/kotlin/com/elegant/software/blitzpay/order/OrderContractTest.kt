package com.elegant.software.blitzpay.order

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.contract.ContractVerifierBase.Companion.fakeJwt
import com.elegant.software.blitzpay.order.api.CheckoutPaymentMethod
import com.elegant.software.blitzpay.order.api.MerchantOrderResponse
import com.elegant.software.blitzpay.order.api.OrderItemResponse
import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.api.OrderSummaryResponse
import com.elegant.software.blitzpay.order.api.PaymentSource
import com.elegant.software.blitzpay.order.api.QrCodeResponse
import com.elegant.software.blitzpay.order.application.OrderMutationConflictException
import com.elegant.software.blitzpay.order.application.OrderService
import com.elegant.software.blitzpay.order.domain.CreatorType
import com.elegant.software.blitzpay.order.domain.OrderStatus
import com.elegant.software.blitzpay.order.domain.OrderType
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.util.UUID

class OrderContractTest : ContractVerifierBase() {
    @MockitoBean
    private lateinit var orderService: OrderService

    private val merchantId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val branchId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    @Test
    fun `post orders returns created order without payment reference`() {
        whenever(orderService.createShopperOrder(any(), any())).thenReturn(
            OrderResponse(
                orderId = "ORD-ABC123456789",
                merchantId = merchantId,
                branchId = branchId,
                orderType = OrderType.PRE_ORDER,
                status = OrderStatus.CREATED,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-abc",
                currency = "EUR",
                totalAmountMinor = 2900,
                paymentRetryAllowed = true,
                checkoutPaymentMethod = CheckoutPaymentMethod.BANK_ACCOUNT,
                items = listOf(
                    OrderItemResponse(
                        itemId = UUID.fromString("44444444-4444-4444-4444-444444444441"),
                        productId = UUID.fromString("22222222-2222-2222-2222-222222222221"),
                        name = "Coffee",
                        quantity = 2,
                        unitPriceMinor = 1250,
                        lineTotalMinor = 2500,
                    )
                ),
                createdAt = Instant.parse("2026-04-30T10:00:00Z"),
            )
        )

        webTestClient.post()
            .uri("/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $fakeJwt")
            .bodyValue(
                """
                {
                  "branchId": "33333333-3333-3333-3333-333333333333",
                  "orderType": "PRE_ORDER",
                  "usesDeferredPayment": false,
                  "customerLocation": null,
                  "items": [
                    { "productId": "22222222-2222-2222-2222-222222222221", "quantity": 2 },
                    { "productId": "22222222-2222-2222-2222-222222222222", "quantity": 1 }
                  ],
                  "paymentMethod": "TRUELAYER"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.orderId").isEqualTo("ORD-ABC123456789")
            .jsonPath("$.branchId").isEqualTo(branchId.toString())
            .jsonPath("$.orderType").isEqualTo("PRE_ORDER")
            .jsonPath("$.status").isEqualTo("CREATED")
            .jsonPath("$.paymentRetryAllowed").isEqualTo(true)
            .jsonPath("$.checkoutPaymentMethod").isEqualTo("BANK_ACCOUNT")
            .jsonPath("$.paymentReference").doesNotExist()
            .jsonPath("$.totalAmountMinor").isEqualTo(2900)
            .jsonPath("$.items[0].itemId").isEqualTo("44444444-4444-4444-4444-444444444441")
    }

    @Test
    fun `post merchant orders returns created status with qr code`() {
        whenever(orderService.createMerchantOrder(any(), any())).thenReturn(
            MerchantOrderResponse(
                orderId = "ORD-MERCHANT00001",
                merchantId = merchantId,
                branchId = branchId,
                orderType = OrderType.WALK_IN_ORDERING,
                status = OrderStatus.CREATED,
                creatorType = CreatorType.MERCHANT,
                currency = "EUR",
                totalAmountMinor = 1250,
                paymentRetryAllowed = true,
                qrCode = QrCodeResponse(paymentUrl = "blitzpay://payment/qr?orderId=ORD-MERCHANT00001&amount=1250&currency=EUR"),
                items = listOf(
                    OrderItemResponse(
                        itemId = UUID.fromString("44444444-4444-4444-4444-444444444441"),
                        productId = UUID.fromString("22222222-2222-2222-2222-222222222221"),
                        name = "Coffee",
                        quantity = 1,
                        unitPriceMinor = 1250,
                        lineTotalMinor = 1250,
                    )
                ),
                createdAt = Instant.parse("2026-04-30T10:00:00Z"),
            )
        )

        webTestClient.post()
            .uri("/v1/merchant/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $fakeJwt")
            .bodyValue(
                """
                {
                  "merchantId": "11111111-1111-1111-1111-111111111111",
                  "branchId": "33333333-3333-3333-3333-333333333333",
                  "orderType": "WALK_IN_ORDERING",
                  "usesDeferredPayment": false,
                  "customerLocation": null,
                  "items": [
                    { "productId": "22222222-2222-2222-2222-222222222221", "quantity": 1 }
                  ]
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.orderId").isEqualTo("ORD-MERCHANT00001")
            .jsonPath("$.orderType").isEqualTo("WALK_IN_ORDERING")
            .jsonPath("$.status").isEqualTo("CREATED")
            .jsonPath("$.paymentRetryAllowed").isEqualTo(true)
            .jsonPath("$.qrCode.paymentUrl").isEqualTo("blitzpay://payment/qr?orderId=ORD-MERCHANT00001&amount=1250&currency=EUR")
    }

    @Test
    fun `get order returns paid status`() {
        whenever(orderService.get("ORD-ABC123456789")).thenReturn(
            OrderResponse(
                orderId = "ORD-ABC123456789",
                merchantId = merchantId,
                branchId = null,
                orderType = OrderType.PRE_ORDER,
                status = OrderStatus.PAID,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-abc",
                currency = "EUR",
                totalAmountMinor = 2900,
                paymentRetryAllowed = false,
                items = emptyList(),
                createdAt = Instant.parse("2026-04-30T10:00:00Z"),
                paidAt = Instant.parse("2026-04-30T10:15:30Z"),
            )
        )

        webTestClient.get()
            .uri("/v1/orders/ORD-ABC123456789")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("PAID")
            .jsonPath("$.paymentRetryAllowed").isEqualTo(false)
            .jsonPath("$.paidAt").isEqualTo("2026-04-30T10:15:30Z")
    }

    @Test
    fun `get order returns APP_PAYMENT paymentSource for app-paid order`() {
        whenever(orderService.get("ORD-PAYNOW00001")).thenReturn(
            OrderResponse(
                orderId = "ORD-PAYNOW00001",
                merchantId = merchantId,
                branchId = branchId,
                orderType = OrderType.PRE_ORDER,
                usesDeferredPayment = false,
                status = OrderStatus.PAID,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-abc",
                currency = "EUR",
                totalAmountMinor = 1250,
                paymentRetryAllowed = false,
                paymentSource = com.elegant.software.blitzpay.order.api.PaymentSource.APP_PAYMENT,
                checkoutPaymentMethod = CheckoutPaymentMethod.CARD,
                items = emptyList(),
                createdAt = Instant.parse("2026-05-15T10:00:00Z"),
                paidAt = Instant.parse("2026-05-15T10:05:00Z"),
            )
        )

        webTestClient.get()
            .uri("/v1/orders/ORD-PAYNOW00001")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("PAID")
            .jsonPath("$.paymentSource").isEqualTo("APP_PAYMENT")
            .jsonPath("$.checkoutPaymentMethod").isEqualTo("CARD")
            .jsonPath("$.paymentRetryAllowed").isEqualTo(false)
    }

    @Test
    fun `post orders with deferred payment returns created order without payment reference`() {
        whenever(orderService.createShopperOrder(any(), any())).thenReturn(
            OrderResponse(
                orderId = "ORD-DEFERRED00001",
                merchantId = merchantId,
                branchId = branchId,
                orderType = OrderType.PRE_ORDER,
                usesDeferredPayment = true,
                status = OrderStatus.CREATED,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-xyz",
                currency = "EUR",
                totalAmountMinor = 1250,
                paymentRetryAllowed = true,
                paymentSource = null,
                items = listOf(
                    OrderItemResponse(
                        itemId = UUID.fromString("44444444-4444-4444-4444-444444444441"),
                        productId = UUID.fromString("22222222-2222-2222-2222-222222222221"),
                        name = "Coffee",
                        quantity = 1,
                        unitPriceMinor = 1250,
                        lineTotalMinor = 1250,
                    )
                ),
                createdAt = Instant.parse("2026-05-15T10:00:00Z"),
            )
        )

        webTestClient.post()
            .uri("/v1/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $fakeJwt")
            .bodyValue(
                """
                {
                  "branchId": "33333333-3333-3333-3333-333333333333",
                  "orderType": "PRE_ORDER",
                  "usesDeferredPayment": true,
                  "customerLocation": null,
                  "items": [
                    { "productId": "22222222-2222-2222-2222-222222222221", "quantity": 1 }
                  ]
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.orderId").isEqualTo("ORD-DEFERRED00001")
            .jsonPath("$.usesDeferredPayment").isEqualTo(true)
            .jsonPath("$.status").isEqualTo("CREATED")
            .jsonPath("$.paymentRetryAllowed").isEqualTo(true)
            .jsonPath("$.paymentReference").doesNotExist()
            .jsonPath("$.paymentSource").doesNotExist()
    }

    @Test
    fun `get shopper orders returns summary list`() {
        whenever(orderService.listShopperOrders(any())).thenReturn(
            listOf(
                OrderSummaryResponse(
                    orderId = "ORD-ABC123456789",
                    merchantId = merchantId,
                    branchId = branchId,
                    orderType = OrderType.PRE_ORDER,
                    status = OrderStatus.PAID,
                    currency = "EUR",
                totalAmountMinor = 2900,
                paymentRetryAllowed = false,
                checkoutPaymentMethod = CheckoutPaymentMethod.TERMINAL,
                createdAt = Instant.parse("2026-04-30T10:00:00Z"),
                )
            )
        )

        webTestClient.get()
            .uri("/v1/orders")
            .header("Authorization", "Bearer $fakeJwt")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].orderId").isEqualTo("ORD-ABC123456789")
            .jsonPath("$[0].status").isEqualTo("PAID")
            .jsonPath("$[0].checkoutPaymentMethod").isEqualTo("TERMINAL")
            .jsonPath("$[0].paymentRetryAllowed").isEqualTo(false)
    }

    // ── US3: Unpaid order mutations ────────────────────────────────────────────

    @Test
    fun `post order items adds item and returns updated order`() {
        val itemId = UUID.fromString("44444444-4444-4444-4444-444444444441")
        whenever(orderService.addItem(eq("ORD-DEFERRED00001"), any(), any())).thenReturn(
            OrderResponse(
                orderId = "ORD-DEFERRED00001",
                merchantId = merchantId,
                branchId = branchId,
                orderType = OrderType.PRE_ORDER,
                usesDeferredPayment = true,
                status = OrderStatus.CREATED,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-xyz",
                currency = "EUR",
                totalAmountMinor = 2500,
                paymentRetryAllowed = true,
                items = listOf(
                    OrderItemResponse(itemId, UUID.fromString("22222222-2222-2222-2222-222222222221"), "Coffee", 2, 1250, 2500),
                ),
                createdAt = Instant.parse("2026-05-15T10:00:00Z"),
            )
        )

        webTestClient.post()
            .uri("/v1/orders/ORD-DEFERRED00001/items")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $fakeJwt")
            .bodyValue("""{"productId": "22222222-2222-2222-2222-222222222221", "quantity": 1}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.orderId").isEqualTo("ORD-DEFERRED00001")
            .jsonPath("$.status").isEqualTo("CREATED")
            .jsonPath("$.totalAmountMinor").isEqualTo(2500)
    }

    @Test
    fun `patch order item quantity returns updated order`() {
        val itemId = UUID.fromString("44444444-4444-4444-4444-444444444441")
        whenever(orderService.updateItemQuantity(eq("ORD-DEFERRED00001"), eq(itemId), any(), any())).thenReturn(
            OrderResponse(
                orderId = "ORD-DEFERRED00001",
                merchantId = merchantId,
                branchId = branchId,
                orderType = OrderType.PRE_ORDER,
                usesDeferredPayment = true,
                status = OrderStatus.CREATED,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-xyz",
                currency = "EUR",
                totalAmountMinor = 3750,
                paymentRetryAllowed = true,
                items = listOf(
                    OrderItemResponse(itemId, UUID.fromString("22222222-2222-2222-2222-222222222221"), "Coffee", 3, 1250, 3750),
                ),
                createdAt = Instant.parse("2026-05-15T10:00:00Z"),
            )
        )

        webTestClient.patch()
            .uri("/v1/orders/ORD-DEFERRED00001/items/$itemId")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $fakeJwt")
            .bodyValue("""{"quantity": 3}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalAmountMinor").isEqualTo(3750)
            .jsonPath("$.items[0].quantity").isEqualTo(3)
    }

    @Test
    fun `delete last order item returns cancelled order`() {
        val itemId = UUID.fromString("44444444-4444-4444-4444-444444444441")
        whenever(orderService.removeItem(eq("ORD-DEFERRED00001"), eq(itemId), any())).thenReturn(
            OrderResponse(
                orderId = "ORD-DEFERRED00001",
                merchantId = merchantId,
                branchId = branchId,
                orderType = OrderType.PRE_ORDER,
                usesDeferredPayment = true,
                status = OrderStatus.CANCELLED,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-xyz",
                currency = "EUR",
                totalAmountMinor = 0,
                paymentRetryAllowed = true,
                items = emptyList(),
                createdAt = Instant.parse("2026-05-15T10:00:00Z"),
            )
        )

        webTestClient.delete()
            .uri("/v1/orders/ORD-DEFERRED00001/items/$itemId")
            .header("Authorization", "Bearer $fakeJwt")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")
            .jsonPath("$.totalAmountMinor").isEqualTo(0)
    }

    @Test
    fun `post cancel order returns cancelled order`() {
        whenever(orderService.cancelOrder(eq("ORD-DEFERRED00001"), any())).thenReturn(
            OrderResponse(
                orderId = "ORD-DEFERRED00001",
                merchantId = merchantId,
                branchId = branchId,
                orderType = OrderType.PRE_ORDER,
                usesDeferredPayment = true,
                status = OrderStatus.CANCELLED,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-xyz",
                currency = "EUR",
                totalAmountMinor = 1250,
                paymentRetryAllowed = true,
                items = emptyList(),
                createdAt = Instant.parse("2026-05-15T10:00:00Z"),
            )
        )

        webTestClient.post()
            .uri("/v1/orders/ORD-DEFERRED00001/cancel")
            .header("Authorization", "Bearer $fakeJwt")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")
    }

    @Test
    fun `mutation on non-CREATED order returns 409`() {
        whenever(orderService.cancelOrder(eq("ORD-PAYNOW00001"), any()))
            .thenThrow(OrderMutationConflictException("Order cannot be mutated in status: PAID"))

        webTestClient.post()
            .uri("/v1/orders/ORD-PAYNOW00001/cancel")
            .header("Authorization", "Bearer $fakeJwt")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    // ── US3: Merchant price override ───────────────────────────────────────────

    @Test
    fun `post merchant appointment order item adds item`() {
        whenever(orderService.addItem(eq("ORD-APPOINTMENT01"), any(), any())).thenReturn(
            appointmentOrderResponse(totalAmountMinor = 3750)
        )

        webTestClient.post()
            .uri("/v1/merchant/orders/ORD-APPOINTMENT01/items")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $fakeJwt")
            .bodyValue("""{"productId":"22222222-2222-2222-2222-222222222221","quantity":1}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalAmountMinor").isEqualTo(3750)
    }

    @Test
    fun `patch merchant appointment order item quantity returns updated order`() {
        val itemId = UUID.fromString("44444444-4444-4444-4444-444444444441")
        whenever(orderService.updateItemQuantity(eq("ORD-APPOINTMENT01"), eq(itemId), any(), any())).thenReturn(
            OrderResponse(
                orderId = "ORD-APPOINTMENT01",
                merchantId = merchantId,
                branchId = branchId,
                orderType = OrderType.PRE_ORDER,
                usesDeferredPayment = true,
                status = OrderStatus.CREATED,
                creatorType = CreatorType.MERCHANT,
                createdById = "merchant-user",
                currency = "EUR",
                totalAmountMinor = 2500,
                paymentRetryAllowed = true,
                items = emptyList(),
                createdAt = Instant.parse("2026-06-09T10:00:00Z"),
            )
        )

        webTestClient.patch()
            .uri("/v1/merchant/orders/ORD-APPOINTMENT01/items/$itemId")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $fakeJwt")
            .bodyValue("""{"quantity": 2}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.totalAmountMinor").isEqualTo(2500)
    }

    @Test
    fun `delete merchant appointment order item returns updated order`() {
        val itemId = UUID.fromString("44444444-4444-4444-4444-444444444441")
        whenever(orderService.removeItem(eq("ORD-APPOINTMENT01"), eq(itemId), any())).thenReturn(
            appointmentOrderResponse(totalAmountMinor = 0, status = OrderStatus.CANCELLED)
        )

        webTestClient.delete()
            .uri("/v1/merchant/orders/ORD-APPOINTMENT01/items/$itemId")
            .header("Authorization", "Bearer $fakeJwt")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")
    }

    @Test
    fun `post merchant appointment order cancel returns cancelled order`() {
        whenever(orderService.cancelOrder(eq("ORD-APPOINTMENT01"), any())).thenReturn(
            appointmentOrderResponse(totalAmountMinor = 1250, status = OrderStatus.CANCELLED)
        )

        webTestClient.post()
            .uri("/v1/merchant/orders/ORD-APPOINTMENT01/cancel")
            .header("Authorization", "Bearer $fakeJwt")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("CANCELLED")
    }

    @Test
    fun `patch merchant order item price applies override and returns updated order`() {
        val itemId = UUID.fromString("44444444-4444-4444-4444-444444444441")
        whenever(orderService.applyMerchantPriceOverride(eq("ORD-DEFERRED00001"), eq(itemId), any(), any())).thenReturn(
            OrderResponse(
                orderId = "ORD-DEFERRED00001",
                merchantId = merchantId,
                branchId = branchId,
                orderType = OrderType.PRE_ORDER,
                usesDeferredPayment = true,
                status = OrderStatus.CREATED,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-xyz",
                currency = "EUR",
                totalAmountMinor = 900,
                paymentRetryAllowed = true,
                items = listOf(
                    OrderItemResponse(
                        itemId,
                        UUID.fromString("22222222-2222-2222-2222-222222222221"),
                        "Coffee",
                        1,
                        900,
                        900,
                    ),
                ),
                createdAt = Instant.parse("2026-05-15T10:00:00Z"),
            )
        )

        webTestClient.patch()
            .uri("/v1/merchant/orders/ORD-DEFERRED00001/items/$itemId/price")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $fakeJwt")
            .bodyValue("""{"unitPriceMinor": 900}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.items[0].unitPriceMinor").isEqualTo(900)
            .jsonPath("$.totalAmountMinor").isEqualTo(900)
    }

    // ── US4: Manual settlement ─────────────────────────────────────────────────

    @Test
    fun `post merchant settle deferred order returns paid with MANUAL_SETTLEMENT source`() {
        whenever(orderService.manualSettle(eq("ORD-DEFERRED00001"), any(), any())).thenReturn(
            OrderResponse(
                orderId = "ORD-DEFERRED00001",
                merchantId = merchantId,
                branchId = branchId,
                orderType = OrderType.PRE_ORDER,
                usesDeferredPayment = true,
                status = OrderStatus.PAID,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-xyz",
                currency = "EUR",
                totalAmountMinor = 1250,
                paymentRetryAllowed = false,
                paymentSource = PaymentSource.MANUAL_SETTLEMENT,
                items = listOf(
                    OrderItemResponse(
                        UUID.fromString("44444444-4444-4444-4444-444444444441"),
                        UUID.fromString("22222222-2222-2222-2222-222222222221"),
                        "Coffee",
                        1,
                        1250,
                        1250,
                    ),
                ),
                createdAt = Instant.parse("2026-05-15T10:00:00Z"),
                paidAt = Instant.parse("2026-05-15T14:00:00Z"),
            )
        )

        webTestClient.post()
            .uri("/v1/merchant/orders/ORD-DEFERRED00001/settle")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $fakeJwt")
            .bodyValue("""{"note": "Customer paid cash at counter"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("PAID")
            .jsonPath("$.paymentSource").isEqualTo("MANUAL_SETTLEMENT")
            .jsonPath("$.paymentRetryAllowed").isEqualTo(false)
    }

    @Test
    fun `post merchant settle already-paid order returns 409`() {
        whenever(orderService.manualSettle(eq("ORD-PAYNOW00001"), any(), any()))
            .thenThrow(OrderMutationConflictException("Order is already paid: ORD-PAYNOW00001"))

        webTestClient.post()
            .uri("/v1/merchant/orders/ORD-PAYNOW00001/settle")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer $fakeJwt")
            .bodyValue("""{}""")
            .exchange()
            .expectStatus().isEqualTo(409)
    }

    // ── US5: Merchant get order by orderId ─────────────────────────────────────

    @Test
    fun `get merchant order by orderId returns full order response`() {
        whenever(orderService.getMerchantOrder("ORD-DEFERRED00001")).thenReturn(
            OrderResponse(
                orderId = "ORD-DEFERRED00001",
                merchantId = merchantId,
                branchId = branchId,
                orderType = OrderType.PRE_ORDER,
                usesDeferredPayment = true,
                status = OrderStatus.PAID,
                creatorType = CreatorType.SHOPPER,
                createdById = "shopper-xyz",
                currency = "EUR",
                totalAmountMinor = 1250,
                paymentRetryAllowed = false,
                paymentSource = PaymentSource.MANUAL_SETTLEMENT,
                items = listOf(
                    OrderItemResponse(
                        UUID.fromString("44444444-4444-4444-4444-444444444441"),
                        UUID.fromString("22222222-2222-2222-2222-222222222221"),
                        "Coffee",
                        1,
                        1250,
                        1250,
                    ),
                ),
                createdAt = Instant.parse("2026-05-15T10:00:00Z"),
                paidAt = Instant.parse("2026-05-15T14:00:00Z"),
            )
        )

        webTestClient.get()
            .uri("/v1/merchant/orders/ORD-DEFERRED00001")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.orderId").isEqualTo("ORD-DEFERRED00001")
            .jsonPath("$.paymentSource").isEqualTo("MANUAL_SETTLEMENT")
            .jsonPath("$.usesDeferredPayment").isEqualTo(true)
    }

    private fun appointmentOrderResponse(
        totalAmountMinor: Long,
        status: OrderStatus = OrderStatus.CREATED,
    ) = OrderResponse(
        orderId = "ORD-APPOINTMENT01",
        merchantId = merchantId,
        branchId = branchId,
        orderType = OrderType.PRE_ORDER,
        usesDeferredPayment = true,
        status = status,
        creatorType = CreatorType.MERCHANT,
        createdById = "merchant-user",
        currency = "EUR",
        totalAmountMinor = totalAmountMinor,
        paymentRetryAllowed = status == OrderStatus.CREATED,
        items = emptyList(),
        createdAt = Instant.parse("2026-06-09T10:00:00Z"),
    )
}
