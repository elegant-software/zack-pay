package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.merchant.api.ActiveBranchInfo
import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.merchant.api.OrderableMerchantProduct
import com.elegant.software.blitzpay.order.api.AddOrderItemRequest
import com.elegant.software.blitzpay.order.api.CreateOrderItemRequest
import com.elegant.software.blitzpay.order.api.CreateOrderRequest
import com.elegant.software.blitzpay.order.api.KitchenEventType
import com.elegant.software.blitzpay.order.api.KitchenOrder
import com.elegant.software.blitzpay.order.api.UpdateOrderItemRequest
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderItem
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.OrderType
import com.elegant.software.blitzpay.order.persistence.model.PreparationStatus
import com.elegant.software.blitzpay.order.persistence.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.persistence.repository.OrderRepository
import com.elegant.software.blitzpay.order.persistence.repository.PaymentAttemptRepository
import com.elegant.software.blitzpay.shopper.api.ShopperProfileGateway
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderPreparationRulesTest {
    private val merchantGateway = mock<MerchantGateway>()
    private val orderRepository = mock<OrderRepository>()
    private val orderItemRepository = mock<OrderItemRepository>()
    private val pickupNumberAllocator = mock<PickupNumberAllocator>()
    private val kitchenOrderBus = mock<KitchenOrderBus>()
    private val events = mock<ApplicationEventPublisher>()
    private val service = OrderService(
        merchantGateway, orderRepository, orderItemRepository, mock<PaymentAttemptRepository>(),
        mock<ShopperProfileGateway>(), events, pickupNumberAllocator, kitchenOrderBus, mock<StorefrontOrderBus>(),
        mock<com.elegant.software.blitzpay.merchant.api.TableGateway>(),
    )

    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val product = OrderableMerchantProduct(productId, merchantId, branchId, "Burger", null, BigDecimal("9.50"), true)

    private fun order(preparationStatus: PreparationStatus) = Order(
        orderId = "ORD-KITCHEN0001",
        merchantApplicationId = merchantId,
        merchantBranchId = branchId,
        creatorType = CreatorType.SHOPPER,
        createdById = "shopper-1",
        currency = "EUR",
        totalAmountMinor = 0,
        itemCount = 0,
        preparationStatus = preparationStatus,
        pickupNumber = 7,
    ).also { whenever(orderRepository.findByOrderId(it.orderId)).thenReturn(it) }

    private fun item(order: Order, quantity: Int = 1, locked: Boolean = false) =
        OrderItem.fromProduct(order.id, product, quantity).also { it.locked = locked }

    private fun stubItems(order: Order, vararg items: OrderItem) {
        whenever(orderItemRepository.findAllByOrderIdFk(order.id)).thenReturn(items.toList())
    }

    @Test
    fun `new orders start RECEIVED with the next pickup number and are announced to the kitchen`() {
        whenever(merchantGateway.findOrderableProducts(any())).thenReturn(listOf(product))
        whenever(merchantGateway.requireActiveBranch(merchantId, branchId)).thenReturn(ActiveBranchInfo(branchId, merchantId, null))
        whenever(merchantGateway.getEnabledOfferings(merchantId)).thenReturn(setOf("PRE_ORDER"))
        whenever(pickupNumberAllocator.next(branchId)).thenReturn(42)
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer { it.arguments[0] }

        val response = service.createShopperOrder(
            CreateOrderRequest(branchId = branchId, orderType = OrderType.PRE_ORDER, items = listOf(CreateOrderItemRequest(productId, 2))),
            "shopper-1",
        )

        assertEquals(42, response.pickupNumber)
        assertEquals(PreparationStatus.RECEIVED, response.preparationStatus)
        val captor = argumentCaptor<KitchenOrder>()
        verify(kitchenOrderBus).publishAfterCommit(eq(branchId), eq(KitchenEventType.CREATED), captor.capture())
        assertEquals(42, captor.firstValue.pickupNumber)
        assertEquals("Burger", captor.firstValue.items.single().name)
    }

    @Test
    fun `quantity changes and removals are allowed while RECEIVED or PREPARING`() {
        listOf(PreparationStatus.RECEIVED, PreparationStatus.PREPARING).forEach { status ->
            val order = order(status)
            val first = item(order)
            val second = item(order)
            stubItems(order, first, second)

            assertEquals(3, service.updateItemQuantity(order.orderId, first.id, UpdateOrderItemRequest(3), "merchant").items.first { it.itemId == first.id }.quantity)
            service.removeItem(order.orderId, second.id, "merchant")
            verify(orderItemRepository).deleteById(second.id)
        }
    }

    @Test
    fun `quantity changes and removals are rejected once READY`() {
        val order = order(PreparationStatus.READY)
        val first = item(order)
        stubItems(order, first, item(order))

        assertFailsWith<OrderMutationConflictException> {
            service.updateItemQuantity(order.orderId, first.id, UpdateOrderItemRequest(2), "merchant")
        }
        assertFailsWith<OrderMutationConflictException> { service.removeItem(order.orderId, first.id, "merchant") }
        assertEquals(1, first.quantity)
        verify(orderItemRepository, never()).deleteById(any())
    }

    @Test
    fun `adding to a READY order locks existing items, keeps the new one unlocked and returns the order to RECEIVED`() {
        val order = order(PreparationStatus.READY)
        val existing = item(order)
        whenever(merchantGateway.findOrderableProducts(listOf(productId))).thenReturn(listOf(product))
        val saved = mutableListOf<OrderItem>()
        whenever(orderItemRepository.save(any<OrderItem>())).thenAnswer { (it.arguments[0] as OrderItem).also(saved::add) }
        whenever(orderItemRepository.findAllByOrderIdFk(order.id)).thenAnswer { listOf(existing) + saved }

        val response = service.addItem(order.orderId, AddOrderItemRequest(productId, 1), "merchant")

        assertEquals(PreparationStatus.RECEIVED, order.preparationStatus)
        assertTrue(existing.locked)
        assertFalse(saved.single().locked)
        assertEquals(listOf(true, false), response.items.map { it.locked })
        verify(kitchenOrderBus).publishAfterCommit(eq(branchId), eq(KitchenEventType.CHANGED), any())
    }

    @Test
    fun `locked items stay unchangeable after the order went back to RECEIVED`() {
        val order = order(PreparationStatus.RECEIVED)
        val made = item(order, locked = true)
        val fresh = item(order)
        stubItems(order, made, fresh)

        assertFailsWith<OrderMutationConflictException> {
            service.updateItemQuantity(order.orderId, made.id, UpdateOrderItemRequest(2), "merchant")
        }
        assertFailsWith<OrderMutationConflictException> { service.removeItem(order.orderId, made.id, "merchant") }
        service.updateItemQuantity(order.orderId, fresh.id, UpdateOrderItemRequest(2), "merchant")
        assertEquals(2, fresh.quantity)
    }

    @Test
    fun `adding to a PREPARING order keeps its preparation status`() {
        val order = order(PreparationStatus.PREPARING)
        val existing = item(order)
        whenever(merchantGateway.findOrderableProducts(listOf(productId))).thenReturn(listOf(product))
        stubItems(order, existing)

        service.addItem(order.orderId, AddOrderItemRequest(productId, 1), "merchant")

        assertEquals(PreparationStatus.PREPARING, order.preparationStatus)
        assertFalse(existing.locked)
    }

    @Test
    fun `cancelling notifies the kitchen with a cancelled order`() {
        val order = order(PreparationStatus.PREPARING)
        stubItems(order, item(order))

        service.cancelOrder(order.orderId, "merchant")

        val captor = argumentCaptor<KitchenOrder>()
        verify(kitchenOrderBus).publishAfterCommit(eq(branchId), eq(KitchenEventType.CANCELLED), captor.capture())
        assertTrue(captor.firstValue.cancelled)
        assertEquals(OrderStatus.CANCELLED, order.status)
    }

    @Test
    fun `removing the last pending line makes only the remaining prepared order ready`() {
        val order = order(PreparationStatus.PREPARING)
        val drinks = item(order, locked = true).also { it.preparationStatus = PreparationStatus.READY }
        val food = item(order)
        stubItems(order, drinks, food)

        service.removeItem(order.orderId, food.id, "merchant")

        assertEquals(PreparationStatus.READY, order.preparationStatus)
        assertEquals(OrderStatus.CREATED, order.status)
        verify(events).publishEvent(any<com.elegant.software.blitzpay.order.api.OrderReadyForPickup>())
    }
}
