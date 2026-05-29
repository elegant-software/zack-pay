package com.elegant.software.blitzpay.order.application

import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.merchant.api.OrderableMerchantProduct
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantBranch
import com.elegant.software.blitzpay.merchant.domain.MerchantOfferingAssignment
import com.elegant.software.blitzpay.merchant.domain.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.order.OrderFixtureLoader
import com.elegant.software.blitzpay.order.api.CheckoutPaymentMethod
import com.elegant.software.blitzpay.order.api.CreateMerchantOrderRequest
import com.elegant.software.blitzpay.order.api.CreateOrderItemRequest
import com.elegant.software.blitzpay.order.api.OrderCustomerLocationRequest
import com.elegant.software.blitzpay.order.api.PaymentMethod
import com.elegant.software.blitzpay.order.domain.Order
import com.elegant.software.blitzpay.order.domain.OrderItem
import com.elegant.software.blitzpay.order.domain.OrderStatus
import com.elegant.software.blitzpay.order.domain.OrderType
import com.elegant.software.blitzpay.order.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.repository.OrderRepository
import com.elegant.software.blitzpay.order.repository.PaymentAttemptRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class OrderServiceTest {
    private val merchantGateway = mock<MerchantGateway>()
    private val merchantApplicationRepository = mock<MerchantApplicationRepository>()
    private val merchantBranchRepository = mock<MerchantBranchRepository>()
    private val merchantOfferingAssignmentRepository = mock<MerchantOfferingAssignmentRepository>()
    private val orderRepository = mock<OrderRepository>()
    private val orderItemRepository = mock<OrderItemRepository>()
    private val paymentAttemptRepository = mock<PaymentAttemptRepository>()
    private val service = OrderService(
        merchantGateway, merchantApplicationRepository, merchantBranchRepository, merchantOfferingAssignmentRepository,
        orderRepository, orderItemRepository, paymentAttemptRepository,
    )

    private val merchantId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val branchId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val merchant = MerchantApplication(
        id = merchantId,
        applicationReference = "BLTZ-ORDER",
        businessProfile = BusinessProfile(
            legalBusinessName = "Order Merchant",
            businessType = "RETAIL",
            registrationNumber = "DE-ORDER",
            operatingCountry = "DE",
            primaryBusinessAddress = "Branch Street 1"
        ),
        primaryContact = PrimaryContact("Owner", "owner@example.com", "+49123")
    )
    private val branch = MerchantBranch(
        id = branchId,
        merchantApplicationId = merchantId,
        name = "Main Branch",
        address = com.elegant.software.blitzpay.merchant.domain.PostalAddress(
            addressLine1 = "Branch Street 1",
            city = "Berlin",
            postalCode = "10115",
            country = "DE",
        ),
    ).also {
        it.location = com.elegant.software.blitzpay.merchant.domain.MerchantLocation(
            latitude = 52.52,
            longitude = 13.405,
            geofenceRadiusMeters = 250
        )
    }

    private fun stubActiveMerchantContext(vararg offerings: String) {
        whenever(merchantApplicationRepository.findById(merchantId)).thenReturn(Optional.of(merchant))
        whenever(merchantBranchRepository.findById(branchId)).thenReturn(Optional.of(branch))
        whenever(merchantOfferingAssignmentRepository.findAllByMerchantApplicationId(merchantId)).thenReturn(
            offerings.map { MerchantOfferingAssignment(merchantId, it) }
        )
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
        assert(response.qrCode.paymentUrl.contains("blitzpay://payment/qr"))
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
        val inactiveMerchant = MerchantApplication(
            id = merchantId,
            applicationReference = "BLTZ-INACTIVE",
            businessProfile = BusinessProfile(
                legalBusinessName = "Inactive Merchant",
                businessType = "RETAIL",
                registrationNumber = "DE-INACTIVE",
                operatingCountry = "DE",
                primaryBusinessAddress = "Branch Street 1"
            ),
            primaryContact = PrimaryContact("Owner", "owner@example.com", "+49123")
        ).also { it.merchantStatus = MerchantOperationalStatus.INACTIVE }
        whenever(merchantApplicationRepository.findById(merchantId)).thenReturn(Optional.of(inactiveMerchant))
        whenever(merchantBranchRepository.findById(branchId)).thenReturn(Optional.of(branch))
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
    fun `createShopperOrder rejects order when branch is inactive`() {
        val request = OrderFixtureLoader.createOrderRequest()
        val inactiveBranch = MerchantBranch(
            id = branchId,
            merchantApplicationId = merchantId,
            name = "Inactive Branch",
            active = false,
            address = com.elegant.software.blitzpay.merchant.domain.PostalAddress(
                addressLine1 = "Branch Street 1",
                city = "Berlin",
                postalCode = "10115",
                country = "DE",
            ),
        )
        whenever(merchantApplicationRepository.findById(merchantId)).thenReturn(Optional.of(merchant))
        whenever(merchantBranchRepository.findById(branchId)).thenReturn(Optional.of(inactiveBranch))
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
}
