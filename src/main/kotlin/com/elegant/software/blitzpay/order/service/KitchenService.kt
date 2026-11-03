package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.order.api.KitchenEventType
import com.elegant.software.blitzpay.order.api.KitchenOrder
import com.elegant.software.blitzpay.order.api.OrderReadyForPickup
import com.elegant.software.blitzpay.order.api.toKitchenOrder
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.PreparationStatus
import com.elegant.software.blitzpay.order.persistence.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.persistence.repository.OrderRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Kitchen Screen use cases: today's open Orders of a Branch and advancing their Preparation Status. */
@Service
@Transactional
class KitchenService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val pickupNumberAllocator: PickupNumberAllocator,
    private val kitchenOrderBus: KitchenOrderBus,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(KitchenService::class.java)

    /**
     * Orders received today (Branch-local day) that are not READY, oldest first. Cancelled ones are included
     * (`cancelled: true`) so a cancellation that happened while a screen was offline still shows; screens dismiss them locally.
     */
    @Transactional(readOnly = true)
    fun listOpenOrders(branchId: UUID): List<KitchenOrder> {
        val zone = pickupNumberAllocator.branchZone(branchId)
        val today = pickupNumberAllocator.businessDay(branchId)
        val orders = orderRepository.findByMerchantBranchIdAndCreatedAtBetween(
            branchId,
            today.atStartOfDay(zone).toInstant(),
            today.plusDays(1).atStartOfDay(zone).toInstant(),
        ).filter { it.isOnKitchenScreen() }.sortedBy { it.createdAt }
        if (orders.isEmpty()) return emptyList()
        val itemsByOrder = orderItemRepository.findAllByOrderIdFkIn(orders.map { it.id }).groupBy { it.orderIdFk }
        return orders.map { it.toKitchenOrder(itemsByOrder[it.id].orEmpty()) }
    }

    fun updatePreparationStatus(branchId: UUID, orderId: UUID, next: PreparationStatus): KitchenOrder {
        val order = orderRepository.findById(orderId).orElse(null)
            ?.takeIf { it.merchantBranchId == branchId }
            ?: throw NoSuchElementException("Order not found: $orderId")
        if (order.status == OrderStatus.CANCELLED) {
            throw OrderMutationConflictException("Order is cancelled: ${order.orderId}")
        }
        if (!order.preparationStatus.canAdvanceTo(next)) {
            throw OrderMutationConflictException(
                "Preparation status cannot change from ${order.preparationStatus} to $next: ${order.orderId}"
            )
        }
        order.advancePreparation(next)
        orderRepository.save(order)
        val kitchenOrder = order.toKitchenOrder(orderItemRepository.findAllByOrderIdFk(order.id))
        kitchenOrderBus.publishAfterCommit(branchId, KitchenEventType.CHANGED, kitchenOrder)
        log.info("preparation status changed orderId={} branchId={} status={}", order.orderId, branchId, next)

        if (next == PreparationStatus.READY) {
            order.linkedShopperId()?.let { shopperId ->
                eventPublisher.publishEvent(
                    OrderReadyForPickup(order.orderId, order.id, order.merchantBranchId, order.pickupNumber, shopperId)
                )
            }
        }
        return kitchenOrder
    }

    private fun Order.isOnKitchenScreen() =
        appointmentId == null && preparationStatus != PreparationStatus.READY

    /** The Target Shopper of an Invoice Order, otherwise the shopper who placed the order. */
    private fun Order.linkedShopperId(): String? =
        targetShopperId ?: createdById.takeIf { creatorType == CreatorType.SHOPPER }
}
