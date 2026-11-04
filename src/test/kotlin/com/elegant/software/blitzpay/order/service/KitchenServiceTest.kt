package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.order.api.KitchenEventType
import com.elegant.software.blitzpay.order.api.OrderReadyForPickup
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderItem
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.PreparationStatus
import com.elegant.software.blitzpay.order.persistence.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.persistence.repository.OrderRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KitchenServiceTest {
    private val orderRepository = mock<OrderRepository>()
    private val orderItemRepository = mock<OrderItemRepository>()
    private val allocator = mock<PickupNumberAllocator>()
    private val bus = mock<KitchenOrderBus>()
    private val storefrontOrderBus = mock<StorefrontOrderBus>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val service = KitchenService(orderRepository, orderItemRepository, allocator, bus, storefrontOrderBus, eventPublisher, org.mockito.kotlin.mock<com.elegant.software.blitzpay.merchant.api.TableGateway>())

    private val branchId = UUID.randomUUID()

    private fun order(
        preparationStatus: PreparationStatus = PreparationStatus.RECEIVED,
        status: OrderStatus = OrderStatus.CREATED,
        creatorType: CreatorType = CreatorType.SHOPPER,
        targetShopperId: String? = null,
        createdAt: Instant = Instant.parse("2026-11-03T09:00:00Z"),
        appointmentId: UUID? = null,
    ) = Order(
        orderId = "ORD-${UUID.randomUUID().toString().take(8)}",
        merchantApplicationId = UUID.randomUUID(),
        merchantBranchId = branchId,
        status = status,
        creatorType = creatorType,
        createdById = if (creatorType == CreatorType.SHOPPER) "shopper-1" else "merchant-user",
        currency = "EUR",
        totalAmountMinor = 100,
        itemCount = 1,
        createdAt = createdAt,
        targetShopperId = targetShopperId,
        appointmentId = appointmentId,
        buyerContactName = "Anna Maria Schmidt",
        preparationStatus = preparationStatus,
        pickupNumber = 3,
    ).also { whenever(orderRepository.findById(it.id)).thenReturn(Optional.of(it)) }

    @Test
    fun `only RECEIVED to PREPARING and PREPARING to READY are valid transitions`() {
        val valid = setOf(
            PreparationStatus.RECEIVED to PreparationStatus.PREPARING,
            PreparationStatus.PREPARING to PreparationStatus.READY,
        )
        PreparationStatus.entries.forEach { from ->
            PreparationStatus.entries.forEach { to -> assertEquals((from to to) in valid, from.canAdvanceTo(to), "$from -> $to") }
        }
    }

    @Test
    fun `listOpenOrders returns today's not-ready orders of the branch-local day incl cancelled, oldest first`() {
        val zone = ZoneId.of("Europe/Berlin")
        whenever(allocator.branchZone(branchId)).thenReturn(zone)
        whenever(allocator.businessDay(branchId)).thenReturn(LocalDate.of(2026, 11, 3))
        val later = order(preparationStatus = PreparationStatus.PREPARING, createdAt = Instant.parse("2026-11-03T10:00:00Z"))
        val earlier = order(createdAt = Instant.parse("2026-11-03T08:00:00Z"))
        val ready = order(preparationStatus = PreparationStatus.READY)
        val cancelled = order(status = OrderStatus.CANCELLED, createdAt = Instant.parse("2026-11-03T11:00:00Z"))
        val appointment = order(appointmentId = UUID.randomUUID())
        whenever(
            orderRepository.findByMerchantBranchIdAndCreatedAtBetween(
                branchId, Instant.parse("2026-11-02T23:00:00Z"), Instant.parse("2026-11-03T23:00:00Z"),
            )
        ).thenReturn(listOf(later, ready, earlier, cancelled, appointment))
        whenever(orderItemRepository.findAllByOrderIdFkIn(any())).thenReturn(emptyList())

        val result = service.listOpenOrders(branchId)

        assertEquals(listOf(earlier.id, later.id, cancelled.id), result.map { it.orderId })
        assertTrue(result.last().cancelled)
        assertEquals("Anna", result.first().shopperFirstName)
        assertEquals(earlier.orderId, result.first().orderCode)
        assertFalse(result.first().cancelled)
    }

    @Test
    fun `advancing emits a change and READY pushes to the linked shopper only`() {
        val order = order()
        whenever(orderItemRepository.findAllByOrderIdFk(order.id)).thenReturn(listOf<OrderItem>())

        service.updatePreparationStatus(branchId, order.id, PreparationStatus.PREPARING)
        verify(eventPublisher, never()).publishEvent(any<Any>())

        val result = service.updatePreparationStatus(branchId, order.id, PreparationStatus.READY)

        assertEquals(PreparationStatus.READY, result.preparationStatus)
        verify(bus, org.mockito.kotlin.times(2)).publishAfterCommit(eq(branchId), eq(KitchenEventType.CHANGED), any())
        verify(storefrontOrderBus, org.mockito.kotlin.times(2)).publishAfterCommit(eq(order), any())
        val captor = argumentCaptor<OrderReadyForPickup>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals("shopper-1", captor.firstValue.shopperId)
        assertEquals(3, captor.firstValue.pickupNumber)
    }

    @Test
    fun `READY on an invoice order pushes to the target shopper, merchant walk-in orders push to nobody`() {
        val invoice = order(preparationStatus = PreparationStatus.PREPARING, creatorType = CreatorType.MERCHANT, targetShopperId = "target-9")
        val walkIn = order(preparationStatus = PreparationStatus.PREPARING, creatorType = CreatorType.MERCHANT)

        service.updatePreparationStatus(branchId, invoice.id, PreparationStatus.READY)
        service.updatePreparationStatus(branchId, walkIn.id, PreparationStatus.READY)

        val captor = argumentCaptor<OrderReadyForPickup>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals("target-9", captor.firstValue.shopperId)
    }

    @Test
    fun `invalid transitions, cancelled orders and other branches are rejected`() {
        val received = order()
        assertFailsWith<OrderMutationConflictException> {
            service.updatePreparationStatus(branchId, received.id, PreparationStatus.READY)
        }
        assertFailsWith<OrderMutationConflictException> {
            service.updatePreparationStatus(branchId, received.id, PreparationStatus.RECEIVED)
        }
        val ready = order(preparationStatus = PreparationStatus.READY)
        assertFailsWith<OrderMutationConflictException> {
            service.updatePreparationStatus(branchId, ready.id, PreparationStatus.PREPARING)
        }
        val cancelled = order(status = OrderStatus.CANCELLED, createdAt = Instant.parse("2026-11-03T11:00:00Z"))
        assertFailsWith<OrderMutationConflictException> {
            service.updatePreparationStatus(branchId, cancelled.id, PreparationStatus.PREPARING)
        }
        assertFailsWith<NoSuchElementException> {
            service.updatePreparationStatus(UUID.randomUUID(), received.id, PreparationStatus.PREPARING)
        }
        assertTrue(received.preparationStatus == PreparationStatus.RECEIVED)
        verify(bus, never()).publishAfterCommit(any(), any(), any())
    }
}
