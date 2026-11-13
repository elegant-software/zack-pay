package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.merchant.api.ActiveBranchInfo
import com.elegant.software.blitzpay.merchant.api.BranchLocationInfo
import com.elegant.software.blitzpay.merchant.api.KioskGateway
import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.order.persistence.model.OrderOrigin
import com.elegant.software.blitzpay.merchant.api.OrderableMerchantProduct
import com.elegant.software.blitzpay.merchant.api.TableGateway
import com.elegant.software.blitzpay.merchant.api.TableInfo
import com.elegant.software.blitzpay.order.OrderFixtureLoader
import com.elegant.software.blitzpay.order.api.CheckoutPaymentMethod
import com.elegant.software.blitzpay.order.api.CreateMerchantOrderRequest
import com.elegant.software.blitzpay.order.api.CreateOrderItemRequest
import com.elegant.software.blitzpay.order.api.FulfilmentType
import com.elegant.software.blitzpay.order.api.OrderCustomerLocationRequest
import com.elegant.software.blitzpay.order.api.PaymentMethod
import com.elegant.software.blitzpay.order.api.StorefrontOrderRequest
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderItem
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.OrderType
import com.elegant.software.blitzpay.order.persistence.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.persistence.repository.OrderRepository
import com.elegant.software.blitzpay.order.persistence.repository.PaymentAttemptRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.springframework.http.HttpStatus
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OrderServiceTest {
    private val merchantGateway = mock<MerchantGateway>()
    private val orderRepository = mock<OrderRepository>()
    private val orderItemRepository = mock<OrderItemRepository>()
    private val paymentAttemptRepository = mock<PaymentAttemptRepository>()
    private val shopperProfileGateway = mock<com.elegant.software.blitzpay.shopper.api.ShopperProfileGateway>()
    private val eventPublisher = mock<org.springframework.context.ApplicationEventPublisher>()
    private val pickupNumberAllocator = mock<PickupNumberAllocator>()
    private val kitchenOrderBus = mock<KitchenOrderBus>()
    private val tableGateway = mock<TableGateway>()
    private val kioskGateway = mock<KioskGateway>()
    private val service = OrderService(
        merchantGateway, orderRepository, orderItemRepository, paymentAttemptRepository, shopperProfileGateway, eventPublisher,
        pickupNumberAllocator, kitchenOrderBus, mock<StorefrontOrderBus>(), tableGateway, kioskGateway,
    )

    private val merchantId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val branchId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val activeBranchInfo = ActiveBranchInfo(
        branchId = branchId,
        merchantId = merchantId,
        location = BranchLocationInfo(latitude = 52.52, longitude = 13.405, geofenceRadiusMeters = 250.0),
    )

    private fun stubActiveMerchantContext(vararg offerings: String) {
        whenever(merchantGateway.requireActiveBranch(merchantId, branchId)).thenReturn(activeBranchInfo)
        whenever(merchantGateway.getEnabledOfferings(merchantId)).thenReturn(offerings.toSet())
    }

    @Test
    fun `createShopperOrder persists order snapshots and totals`() {
        val request = OrderFixtureLoader.createOrderRequest()
        stubActiveMerchantContext("PRE_ORDER")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(request.items[0].productId, merchantId, branchId, "Coffee", "Medium roast", BigDecimal("12.50"), true),
                OrderableMerchantProduct(request.items[1].productId, merchantId, branchId, "Bagel", "Sesame", BigDecimal("4.00"), true),
            )
        )
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }
        val response = service.createShopperOrder(request, "shopper-abc")

        assertEquals(2_900L, response.totalAmountMinor)
        assertEquals(merchantId, response.merchantId)
        assertEquals(branchId, response.branchId)
        assertEquals(2, response.items.size)
        assertEquals("Coffee", response.items.first().name)
        assertEquals(2_500L, response.items.first().lineTotalMinor)
        assertEquals(OrderStatus.CREATED, response.status)
        assertEquals(OrderType.PRE_ORDER, response.orderType)
        assertEquals(PaymentMethod.TRUELAYER, request.paymentMethod)
        assertEquals(CheckoutPaymentMethod.BANK_ACCOUNT, response.checkoutPaymentMethod)
        assertNull(response.paymentReference)
        assertNull(response.lastPaymentRequestId)
        assertNull(response.lastPaymentProvider)
        verify(paymentAttemptRepository, never()).save(any())
    }

    @Test
    fun `createShopperOrder does not guess card for generic Stripe payment method`() {
        val request = OrderFixtureLoader.createOrderRequest().copy(paymentMethod = PaymentMethod.STRIPE)
        stubActiveMerchantContext("PRE_ORDER")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(request.items[0].productId, merchantId, branchId, "Coffee", "Medium roast", BigDecimal("12.50"), true),
                OrderableMerchantProduct(request.items[1].productId, merchantId, branchId, "Bagel", "Sesame", BigDecimal("4.00"), true),
            )
        )
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }

        val response = service.createShopperOrder(request, "shopper-abc")

        assertNull(response.checkoutPaymentMethod)
    }

    @Test
    fun `createShopperOrder rejects missing products`() {
        val request = OrderFixtureLoader.createOrderRequest()
        stubActiveMerchantContext("PRE_ORDER")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(emptyList())

        assertFailsWith<NoSuchElementException> {
            service.createShopperOrder(request, "shopper-abc")
        }
    }

    @Test
    fun `createShopperOrder rejects inactive products`() {
        val request = OrderFixtureLoader.createOrderRequest()
        stubActiveMerchantContext("PRE_ORDER")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(request.items[0].productId, merchantId, null, "Coffee", null, BigDecimal("12.50"), false),
                OrderableMerchantProduct(request.items[1].productId, merchantId, null, "Bagel", null, BigDecimal("4.00"), true),
            )
        )

        assertFailsWith<OrderCreationConflictException> {
            service.createShopperOrder(request, "shopper-abc")
        }
    }

    @Test
    fun `createShopperOrder rejects cross merchant products`() {
        val request = OrderFixtureLoader.createOrderRequest()
        stubActiveMerchantContext("PRE_ORDER")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(request.items[0].productId, UUID.randomUUID(), null, "Coffee", null, BigDecimal("12.50"), true),
                OrderableMerchantProduct(request.items[1].productId, UUID.randomUUID(), null, "Bagel", null, BigDecimal("4.00"), true),
            )
        )

        assertFailsWith<OrderCreationConflictException> {
            service.createShopperOrder(request, "shopper-abc")
        }
    }

    @Test
    fun `createMerchantOrder returns CREATED status with QR code`() {
        val productId1 = UUID.fromString("22222222-2222-2222-2222-222222222221")
        val productId2 = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val request = CreateMerchantOrderRequest(
            merchantId = merchantId,
            branchId = branchId,
            orderType = OrderType.WALK_IN_ORDERING,
            items = listOf(
                CreateOrderItemRequest(productId1, 2),
                CreateOrderItemRequest(productId2, 1),
            ),
        )
        stubActiveMerchantContext("WALK_IN_ORDERING")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(productId1, merchantId, branchId, "Coffee", "Medium roast", BigDecimal("12.50"), true),
                OrderableMerchantProduct(productId2, merchantId, branchId, "Bagel", "Sesame", BigDecimal("4.00"), true),
            )
        )
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }

        val response = service.createMerchantOrder(request, "merchant-user-xyz")

        assertEquals(OrderStatus.CREATED, response.status)
        assertEquals(2_900L, response.totalAmountMinor)
        assertEquals(merchantId, response.merchantId)
        assertEquals(OrderType.WALK_IN_ORDERING, response.orderType)
        assert(response.qrCode!!.paymentUrl.contains("blitzpay://payment/qr"))
    }

    @Test
    fun `createShopperOrder rejects walk in ordering when customer is outside branch geofence`() {
        val request = OrderFixtureLoader.createOrderRequest().copy(
            orderType = OrderType.WALK_IN_ORDERING,
            customerLocation = OrderCustomerLocationRequest(
                latitude = 48.1372,
                longitude = 11.5756,
                accuracyMeters = 20.0,
                capturedAt = Instant.now()
            )
        )
        stubActiveMerchantContext("WALK_IN_ORDERING")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(request.items[0].productId, merchantId, branchId, "Coffee", "Medium roast", BigDecimal("12.50"), true),
                OrderableMerchantProduct(request.items[1].productId, merchantId, branchId, "Bagel", "Sesame", BigDecimal("4.00"), true),
            )
        )

        assertFailsWith<OrderCreationConflictException> {
            service.createShopperOrder(request, "shopper-abc")
        }
    }

    @Test
    fun `createShopperOrder rejects order when merchant is inactive`() {
        val request = OrderFixtureLoader.createOrderRequest()
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(request.items[0].productId, merchantId, branchId, "Coffee", "Medium roast", BigDecimal("12.50"), true),
                OrderableMerchantProduct(request.items[1].productId, merchantId, branchId, "Bagel", "Sesame", BigDecimal("4.00"), true),
            )
        )
        whenever(merchantGateway.getEnabledOfferings(merchantId)).thenReturn(setOf("PRE_ORDER"))
        whenever(merchantGateway.requireActiveBranch(merchantId, branchId))
            .thenThrow(IllegalStateException("Merchant is inactive: $merchantId"))

        assertFailsWith<OrderCreationConflictException> {
            service.createShopperOrder(request, "shopper-abc")
        }
    }

    @Test
    fun `createShopperOrder rejects order when branch is inactive`() {
        val request = OrderFixtureLoader.createOrderRequest()
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(request.items[0].productId, merchantId, branchId, "Coffee", "Medium roast", BigDecimal("12.50"), true),
                OrderableMerchantProduct(request.items[1].productId, merchantId, branchId, "Bagel", "Sesame", BigDecimal("4.00"), true),
            )
        )
        whenever(merchantGateway.getEnabledOfferings(merchantId)).thenReturn(setOf("PRE_ORDER"))
        whenever(merchantGateway.requireActiveBranch(merchantId, branchId))
            .thenThrow(IllegalStateException("Branch is inactive: $branchId"))

        assertFailsWith<OrderCreationConflictException> {
            service.createShopperOrder(request, "shopper-abc")
        }
    }

    @Test
    fun `createShopperOrder rejects deferred payment when merchant lacks DEFERRED_PAYMENT offering`() {
        val request = OrderFixtureLoader.createOrderRequest().copy(
            usesDeferredPayment = true
        )
        stubActiveMerchantContext("PRE_ORDER")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(request.items[0].productId, merchantId, branchId, "Coffee", "Medium roast", BigDecimal("12.50"), true),
                OrderableMerchantProduct(request.items[1].productId, merchantId, branchId, "Bagel", "Sesame", BigDecimal("4.00"), true),
            )
        )

        assertFailsWith<OrderCreationConflictException> {
            service.createShopperOrder(request, "shopper-abc")
        }
    }

    @Test
    fun `createShopperOrder accepts deferred payment when merchant has DEFERRED_PAYMENT offering`() {
        val request = OrderFixtureLoader.createOrderRequest().copy(
            usesDeferredPayment = true
        )
        stubActiveMerchantContext("PRE_ORDER", "DEFERRED_PAYMENT")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(
                OrderableMerchantProduct(request.items[0].productId, merchantId, branchId, "Coffee", "Medium roast", BigDecimal("12.50"), true),
                OrderableMerchantProduct(request.items[1].productId, merchantId, branchId, "Bagel", "Sesame", BigDecimal("4.00"), true),
            )
        )
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }

        val response = service.createShopperOrder(request, "shopper-abc")

        assertEquals(true, response.usesDeferredPayment)
        assertEquals(OrderStatus.CREATED, response.status)
    }

    @Test
    fun `createMerchantOrder with targetShopperId creates invoice order and publishes event`() {
        val productId1 = UUID.fromString("22222222-2222-2222-2222-222222222221")
        val targetShopperId = "shopper-target-123"
        val request = CreateMerchantOrderRequest(
            merchantId = merchantId,
            branchId = branchId,
            orderType = OrderType.WALK_IN_ORDERING,
            usesDeferredPayment = true,
            targetShopperId = targetShopperId,
            dueDate = java.time.LocalDate.of(2026, 7, 1),
            items = listOf(CreateOrderItemRequest(productId1, 2)),
        )
        stubActiveMerchantContext("WALK_IN_ORDERING", "DEFERRED_PAYMENT")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(OrderableMerchantProduct(productId1, merchantId, branchId, "Coffee", "Medium roast", BigDecimal("12.50"), true))
        )
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }

        val response = service.createMerchantOrder(request, "merchant-user-xyz")

        assertEquals(targetShopperId, response.targetShopperId)
        assertEquals(java.time.LocalDate.of(2026, 7, 1), response.dueDate)
        assertNull(response.qrCode)
        val captor = argumentCaptor<com.elegant.software.blitzpay.order.api.InvoiceOrderCreated>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals(targetShopperId, captor.firstValue.targetShopperId)
    }

    @Test
    fun `manualSettle marks a shopper's cash order paid and tells the shopper`() {
        val order = Order(
            orderId = "ORD-CASH0001",
            merchantApplicationId = merchantId,
            merchantBranchId = branchId,
            status = OrderStatus.CREATED,
            creatorType = CreatorType.SHOPPER,
            createdById = "shopper-1",
            currency = "EUR",
            totalAmountMinor = 100,
            itemCount = 1,
            checkoutPaymentMethod = "CASH",
        )
        whenever(orderRepository.findByOrderId("ORD-CASH0001")).thenReturn(order)

        val response = service.manualSettle("ORD-CASH0001", com.elegant.software.blitzpay.order.api.SettleOrderRequest(), "staff-1")

        assertEquals(OrderStatus.PAID, response.status)
        verify(eventPublisher).publishEvent(com.elegant.software.blitzpay.order.api.OrderManuallySettled("ORD-CASH0001", "shopper-1"))
    }

    @Test
    fun `createMerchantOrder rejects targetShopperId without deferred payment`() {
        val productId1 = UUID.fromString("22222222-2222-2222-2222-222222222221")
        val request = CreateMerchantOrderRequest(
            merchantId = merchantId,
            branchId = branchId,
            orderType = OrderType.WALK_IN_ORDERING,
            usesDeferredPayment = false,
            targetShopperId = "shopper-target-123",
            items = listOf(CreateOrderItemRequest(productId1, 1)),
        )

        assertFailsWith<IllegalArgumentException> {
            service.createMerchantOrder(request, "merchant-user-xyz")
        }
    }

    @Test
    fun `deleteOrder deletes payment attempts and items before the order`() {
        val orderId = UUID.randomUUID()
        val order = mock<Order>()
        whenever(order.id).thenReturn(orderId)
        whenever(orderRepository.findById(orderId)).thenReturn(Optional.of(order))

        service.deleteOrder(orderId)

        inOrder(paymentAttemptRepository, orderItemRepository, orderRepository) {
            verify(paymentAttemptRepository).deleteAllByOrderIdFk(orderId)
            verify(orderItemRepository).deleteAllByOrderIdFk(orderId)
            verify(orderRepository).delete(order)
        }
    }

    private fun stubGuestCatalog(productId: UUID) {
        stubActiveMerchantContext("PRE_ORDER")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(OrderableMerchantProduct(productId, merchantId, branchId, "Coffee", "Medium roast", BigDecimal("12.50"), true)),
        )
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `createGuestOrder prices from catalog and stores only the token hash`() {
        val productId = UUID.randomUUID()
        stubGuestCatalog(productId)

        val created = service.createGuestOrder(StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(productId, 2))))

        val saved = argumentCaptor<Order>().also { verify(orderRepository).save(it.capture()) }.firstValue
        assertEquals(2_500L, created.order.totalAmountMinor)
        assertEquals(CreatorType.GUEST, saved.creatorType)
        assertTrue(created.token.length >= 43, "256-bit token expected")
        assertNotEquals(created.token, saved.guestTokenHash)
        assertTrue(GuestToken.matches(saved, created.token))
        assertTrue(saved.guestTokenExpiresAt!!.isAfter(Instant.now().plusSeconds(55 * 60)))
    }

    private fun kiosk(branch: UUID = branchId) = branch

    @Test
    fun `kiosk order needs no location or table, is walk-in and marked KIOSK`() {
        val productId = UUID.randomUUID()
        stubGuestCatalog(productId)
        stubActiveMerchantContext("WALK_IN_ORDERING")

        val created = service.createGuestOrder(
            StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(productId, 1))), kiosk(),
        )

        val saved = argumentCaptor<Order>().also { verify(orderRepository).save(it.capture()) }.firstValue
        assertEquals(OrderOrigin.KIOSK, saved.origin)
        assertEquals(OrderOrigin.KIOSK, created.order.origin)
        assertEquals(OrderType.WALK_IN_ORDERING, saved.orderType)
        assertNull(saved.tableId)
        assertEquals(CreatorType.GUEST, saved.creatorType)
    }

    @Test
    fun `without a kiosk a walk-in guest order still needs a location and origin stays STANDARD`() {
        val productId = UUID.randomUUID()
        stubGuestCatalog(productId)
        stubActiveMerchantContext("PRE_ORDER", "WALK_IN_ORDERING")
        whenever(tableGateway.findTableByCode("T1")).thenReturn(TableInfo(UUID.randomUUID(), branchId, "Table 1", true))

        val ex = assertFailsWith<ApiException> {
            service.createGuestOrder(StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(productId, 1)), tableCode = "T1"))
        }
        assertEquals("LOCATION_REQUIRED", ex.code)

        service.createGuestOrder(StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(productId, 1))))
        val saved = argumentCaptor<Order>().also { verify(orderRepository).save(it.capture()) }.firstValue
        assertEquals(OrderOrigin.STANDARD, saved.origin)
        assertEquals(OrderType.PRE_ORDER, saved.orderType)
    }

    @Test
    fun `a kiosk of another branch cannot order for this branch`() {
        val productId = UUID.randomUUID()
        stubGuestCatalog(productId)

        val ex = assertFailsWith<ApiException> {
            service.createGuestOrder(
                StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(productId, 1))), kiosk(UUID.randomUUID()),
            )
        }
        assertEquals(HttpStatus.FORBIDDEN, ex.status)
        assertEquals(OrderService.KIOSK_BRANCH_MISMATCH, ex.code)
        verify(orderRepository, never()).save(any<Order>())
    }

    @Test
    fun `kiosk cash order is rejected when CASH is disabled in Kiosk Settings`() {
        val productId = UUID.randomUUID()
        stubGuestCatalog(productId)
        stubActiveMerchantContext("WALK_IN_ORDERING")
        whenever(kioskGateway.findKioskPaymentChoices(branchId)).thenReturn(setOf("CARD", "SCAN_TO_PAY"))
        val request = StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(productId, 1)), checkoutPaymentMethod = CheckoutPaymentMethod.CASH)

        val ex = assertFailsWith<ApiException> { service.createGuestOrder(request, kiosk()) }
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.status)
        assertEquals(OrderService.KIOSK_PAYMENT_CHOICE_DISABLED, ex.code)

        whenever(kioskGateway.findKioskPaymentChoices(branchId)).thenReturn(setOf("CASH"))
        assertEquals(CheckoutPaymentMethod.CASH, service.createGuestOrder(request, kiosk()).order.checkoutPaymentMethod)
    }

    @Test
    fun `non-kiosk guest cash order ignores Kiosk Settings`() {
        val productId = UUID.randomUUID()
        stubGuestCatalog(productId)
        val request = StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(productId, 1)), checkoutPaymentMethod = CheckoutPaymentMethod.CASH)

        service.createGuestOrder(request)

        verify(kioskGateway, never()).findKioskPaymentChoices(any())
    }

    @Test
    fun `createGuestOrder rejects oversized quantities`() {
        assertFailsWith<IllegalArgumentException> {
            service.createGuestOrder(StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(UUID.randomUUID(), 100))))
        }
    }

    @Test
    fun `createGuestOrder stores the invoice email with consent time and version`() {
        val productId = UUID.randomUUID()
        stubGuestCatalog(productId)

        service.createGuestOrder(
            StorefrontOrderRequest(
                branchId, listOf(CreateOrderItemRequest(productId, 1)),
                buyerEmail = " guest@example.com ", invoiceEmailConsent = true,
            )
        )

        val saved = argumentCaptor<Order>().also { verify(orderRepository).save(it.capture()) }.firstValue
        assertEquals("guest@example.com", saved.invoiceEmail)
        assertNotNull(saved.invoiceEmailConsentAt)
        assertEquals(OrderService.INVOICE_EMAIL_CONSENT_VERSION, saved.invoiceEmailConsentVersion)
    }

    @Test
    fun `createGuestOrder stores no consent without an email`() {
        val productId = UUID.randomUUID()
        stubGuestCatalog(productId)

        service.createGuestOrder(StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(productId, 1)), invoiceEmailConsent = true))

        val saved = argumentCaptor<Order>().also { verify(orderRepository).save(it.capture()) }.firstValue
        assertNull(saved.invoiceEmail)
        assertNull(saved.invoiceEmailConsentAt)
    }

    @Test
    fun `createGuestOrder rejects an email without consent`() {
        val ex = assertFailsWith<ApiException> {
            service.createGuestOrder(
                StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(UUID.randomUUID(), 1)), buyerEmail = "guest@example.com")
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
        assertEquals(OrderService.INVOICE_EMAIL_CONSENT_REQUIRED, ex.code)
        verify(orderRepository, never()).save(any<Order>())
    }

    @Test
    fun `createGuestOrder rejects a malformed email`() {
        listOf("no-at-sign", "a@b", "a b@example.com").forEach { email ->
            assertFailsWith<IllegalArgumentException>(email) {
                service.createGuestOrder(
                    StorefrontOrderRequest(
                        branchId, listOf(CreateOrderItemRequest(UUID.randomUUID(), 1)),
                        buyerEmail = email, invoiceEmailConsent = true,
                    )
                )
            }
        }
    }

    @Test
    fun `getGuestOrder hides the order for a missing, wrong, expired or other order's token`() {
        val productId = UUID.randomUUID()
        stubGuestCatalog(productId)
        val created = service.createGuestOrder(StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(productId, 1))))
        val saved = argumentCaptor<Order>().also { verify(orderRepository).save(it.capture()) }.firstValue
        whenever(orderRepository.findByOrderId(saved.orderId)).thenReturn(saved)
        whenever(orderItemRepository.findAllByOrderIdFk(saved.id)).thenReturn(emptyList())

        assertEquals(saved.orderId, service.getGuestOrder(saved.orderId, created.token).orderId)
        listOf(null, "", "wrong", GuestToken.generate()).forEach { token ->
            assertFailsWith<NoSuchElementException> { service.getGuestOrder(saved.orderId, token) }
        }
        assertFalse(GuestToken.matches(saved, created.token, now = saved.guestTokenExpiresAt!!.plusSeconds(1)))
    }

    // --- ordering at a Table ---

    private val tableId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val nearBranch = OrderCustomerLocationRequest(latitude = 52.5201, longitude = 13.4051, accuracyMeters = 15.0, capturedAt = Instant.now())

    private fun stubTableCatalog(productId: UUID, vararg offerings: String = arrayOf("WALK_IN_ORDERING")) {
        stubActiveMerchantContext(*offerings)
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(OrderableMerchantProduct(productId, merchantId, branchId, "Coffee", null, BigDecimal("3.50"), true)),
        )
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }
    }

    private fun tableOrder(productId: UUID, tableCode: String = "K7M2QX", orderType: OrderType = OrderType.WALK_IN_ORDERING) =
        com.elegant.software.blitzpay.order.api.CreateOrderRequest(
            branchId = branchId,
            orderType = orderType,
            customerLocation = nearBranch,
            items = listOf(CreateOrderItemRequest(productId, 1)),
            tableCode = tableCode,
        )

    @Test
    fun `createShopperOrder at a Table snapshots the Table and its name`() {
        val productId = UUID.randomUUID()
        stubTableCatalog(productId)
        whenever(tableGateway.findTableByCode("K7M2QX")).thenReturn(TableInfo(tableId, branchId, "Terrace 3", true))

        val response = service.createShopperOrder(tableOrder(productId), "shopper-abc")

        val saved = argumentCaptor<Order>().also { verify(orderRepository).save(it.capture()) }.firstValue
        assertEquals(tableId, saved.tableId)
        assertEquals("Terrace 3", saved.tableName)
        assertEquals(tableId, response.tableId)
        assertEquals("Terrace 3", response.tableName)
        assertEquals(OrderType.WALK_IN_ORDERING, response.orderType)
        verify(tableGateway, never()).seatGuestsIfUnset(any(), any())
    }

    @Test
    fun `createShopperOrder at a Table defaults the Fulfilment Type to DINE_IN`() {
        val productId = UUID.randomUUID()
        stubTableCatalog(productId)
        whenever(tableGateway.findTableByCode("K7M2QX")).thenReturn(TableInfo(tableId, branchId, "Terrace 3", true))

        val response = service.createShopperOrder(tableOrder(productId), "shopper-abc")

        assertEquals(FulfilmentType.DINE_IN, response.fulfilmentType)
    }

    @Test
    fun `createShopperOrder keeps an explicit Fulfilment Type even at a Table`() {
        val productId = UUID.randomUUID()
        stubTableCatalog(productId)
        whenever(tableGateway.findTableByCode("K7M2QX")).thenReturn(TableInfo(tableId, branchId, "Terrace 3", true))

        val response = service.createShopperOrder(tableOrder(productId).copy(fulfilmentType = FulfilmentType.TAKE_AWAY), "shopper-abc")

        assertEquals(FulfilmentType.TAKE_AWAY, response.fulfilmentType)
    }

    @Test
    fun `createShopperOrder without a Table defaults the Fulfilment Type to TAKE_AWAY`() {
        val request = OrderFixtureLoader.createOrderRequest()
        stubActiveMerchantContext("PRE_ORDER")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            request.items.map { OrderableMerchantProduct(it.productId, merchantId, branchId, "Item", null, BigDecimal("1.00"), true) }
        )
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }

        assertEquals(FulfilmentType.TAKE_AWAY, service.createShopperOrder(request, "shopper-abc").fulfilmentType)
    }

    @Test
    fun `createMerchantOrder keeps the staff choice and gives Invoice Orders none`() {
        val productId = UUID.randomUUID()
        stubActiveMerchantContext("WALK_IN_ORDERING", "DEFERRED_PAYMENT")
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(
            listOf(OrderableMerchantProduct(productId, merchantId, branchId, "Coffee", null, BigDecimal("2.00"), true))
        )
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }
        val base = CreateMerchantOrderRequest(merchantId, branchId, items = listOf(CreateOrderItemRequest(productId, 1)))

        assertEquals(FulfilmentType.DINE_IN, service.createMerchantOrder(base.copy(fulfilmentType = FulfilmentType.DINE_IN), "staff").fulfilmentType)
        assertEquals(FulfilmentType.TAKE_AWAY, service.createMerchantOrder(base, "staff").fulfilmentType)
        val invoice = base.copy(usesDeferredPayment = true, targetShopperId = "shopper-1", fulfilmentType = FulfilmentType.DINE_IN)
        assertNull(service.createMerchantOrder(invoice, "staff").fulfilmentType)
    }

    @Test
    fun `createShopperOrder passes the Guest Count to the Table and rejects out of range counts`() {
        val productId = UUID.randomUUID()
        stubTableCatalog(productId)
        whenever(tableGateway.findTableByCode("K7M2QX")).thenReturn(TableInfo(tableId, branchId, "5", true))

        service.createShopperOrder(tableOrder(productId).copy(guestCount = 3), "shopper-abc")

        verify(tableGateway).seatGuestsIfUnset(tableId, 3)
        assertFailsWith<IllegalArgumentException> { service.createShopperOrder(tableOrder(productId).copy(guestCount = 51), "s") }
    }

    @Test
    fun `createShopperOrder at a Table must be a walk in order`() {
        val productId = UUID.randomUUID()
        stubTableCatalog(productId, "PRE_ORDER", "WALK_IN_ORDERING")
        whenever(tableGateway.findTableByCode("K7M2QX")).thenReturn(TableInfo(tableId, branchId, "5", true))

        assertFailsWith<IllegalArgumentException> {
            service.createShopperOrder(tableOrder(productId, orderType = OrderType.PRE_ORDER), "shopper-abc")
        }
        verify(orderRepository, never()).save(any())
    }

    @Test
    fun `createShopperOrder rejects unknown, inactive and other branch Tables`() {
        val productId = UUID.randomUUID()
        stubTableCatalog(productId)
        whenever(tableGateway.findTableByCode("UNKNWN")).thenReturn(null)
        whenever(tableGateway.findTableByCode("OFF111")).thenReturn(TableInfo(tableId, branchId, "5", false))
        whenever(tableGateway.findTableByCode("OTHER2")).thenReturn(TableInfo(tableId, UUID.randomUUID(), "5", true))

        assertFailsWith<NoSuchElementException> { service.createShopperOrder(tableOrder(productId, "UNKNWN"), "s") }
        assertFailsWith<OrderCreationConflictException> { service.createShopperOrder(tableOrder(productId, "OFF111"), "s") }
        assertFailsWith<OrderCreationConflictException> { service.createShopperOrder(tableOrder(productId, "OTHER2"), "s") }
        verify(orderRepository, never()).save(any())
    }

    @Test
    fun `createShopperOrder at a Table needs Walk-In Ordering and a nearby location`() {
        val productId = UUID.randomUUID()
        stubTableCatalog(productId, "PRE_ORDER")
        whenever(tableGateway.findTableByCode("K7M2QX")).thenReturn(TableInfo(tableId, branchId, "5", true))
        assertFailsWith<OrderCreationConflictException> { service.createShopperOrder(tableOrder(productId), "s") }

        stubTableCatalog(productId)
        // Clients show their own text per code, so each location failure needs a distinct one.
        fun locationCode(location: OrderCustomerLocationRequest?) = assertFailsWith<ApiException> {
            service.createShopperOrder(tableOrder(productId).copy(customerLocation = location), "s")
        }.code
        assertEquals("LOCATION_REQUIRED", locationCode(null))
        assertEquals("LOCATION_INACCURATE", locationCode(nearBranch.copy(accuracyMeters = 5_000.0)))
        assertEquals("LOCATION_EXPIRED", locationCode(nearBranch.copy(capturedAt = Instant.now().minusSeconds(3_600))))
        assertEquals("OUTSIDE_GEOFENCE", locationCode(nearBranch.copy(latitude = 48.1372, longitude = 11.5756)))
    }

    @Test
    fun `createGuestOrder with a tableCode is a walk in order at the Table, never deferred`() {
        val productId = UUID.randomUUID()
        stubTableCatalog(productId)
        whenever(tableGateway.findTableByCode("K7M2QX")).thenReturn(TableInfo(tableId, branchId, "7", true))

        val created = service.createGuestOrder(
            StorefrontOrderRequest(
                branchId = branchId,
                items = listOf(CreateOrderItemRequest(productId, 1)),
                tableCode = "K7M2QX",
                customerLocation = nearBranch,
                checkoutPaymentMethod = CheckoutPaymentMethod.CASH,
            )
        )

        val saved = argumentCaptor<Order>().also { verify(orderRepository).save(it.capture()) }.firstValue
        assertEquals(OrderType.WALK_IN_ORDERING, saved.orderType)
        assertFalse(saved.usesDeferredPayment)
        assertEquals(tableId, saved.tableId)
        assertEquals("7", created.order.tableName)
        assertEquals(CheckoutPaymentMethod.CASH, created.order.checkoutPaymentMethod)
        assertEquals(OrderStatus.CREATED, created.order.status)

        val terminal = service.createGuestOrder(
            StorefrontOrderRequest(
                branchId = branchId,
                items = listOf(CreateOrderItemRequest(productId, 1)),
                tableCode = "K7M2QX",
                customerLocation = nearBranch,
                checkoutPaymentMethod = CheckoutPaymentMethod.TERMINAL,
            )
        )
        assertEquals(CheckoutPaymentMethod.TERMINAL, terminal.order.checkoutPaymentMethod)
    }

    @Test
    fun `createGuestOrder accepts only CASH or TERMINAL as checkout payment method`() {
        assertFailsWith<IllegalArgumentException> {
            service.createGuestOrder(
                StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(UUID.randomUUID(), 1)), checkoutPaymentMethod = CheckoutPaymentMethod.CARD)
            )
        }
    }

    @Test
    fun `createGuestOrder with a tableCode requires the customer location`() {
        val productId = UUID.randomUUID()
        stubTableCatalog(productId)
        whenever(tableGateway.findTableByCode("K7M2QX")).thenReturn(TableInfo(tableId, branchId, "7", true))

        val error = assertFailsWith<ApiException> {
            service.createGuestOrder(StorefrontOrderRequest(branchId, listOf(CreateOrderItemRequest(productId, 1)), tableCode = "K7M2QX"))
        }
        assertEquals("LOCATION_REQUIRED", error.code)
    }
}
