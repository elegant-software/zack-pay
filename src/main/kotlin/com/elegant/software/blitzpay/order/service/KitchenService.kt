package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.merchant.api.PreparationStation
import com.elegant.software.blitzpay.order.api.forStation
import com.elegant.software.blitzpay.merchant.api.TableGateway
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
    private val storefrontOrderBus: StorefrontOrderBus,
    private val eventPublisher: ApplicationEventPublisher,
    private val tableGateway: TableGateway,
    private val merchantGateway: MerchantGateway,
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
        val order = orderRepository.findByIdForPreparationUpdate(orderId)
            ?.takeIf { it.merchantBranchId == branchId }
            ?: throw NoSuchElementException("Order not found: $orderId")
        if (order.status == OrderStatus.CANCELLED) {
            throw OrderMutationConflictException("Order is cancelled: ${order.orderId}")
        }
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        if (items.any { it.preparationStationId != UUID(0, 0) }) {
            throw OrderMutationConflictException("Select a preparation station to update this order")
        }
        // A screen may retry after losing the response to a successful update.
        // Return the current ticket without repeating events or shopper notifications.
        if (next != PreparationStatus.RECEIVED && order.preparationStatus == next) {
            return order.toKitchenOrder(items)
        }
        if (!order.preparationStatus.canAdvanceTo(next)) {
            throw OrderMutationConflictException(
                "Preparation status cannot change from ${order.preparationStatus} to $next: ${order.orderId}"
            )
        }
        order.advancePreparation(next)
        orderRepository.save(order)
        items.forEach { it.preparationStatus = next }
        orderItemRepository.saveAll(items)
        val kitchenOrder = order.toKitchenOrder(items)
        kitchenOrderBus.publishAfterCommit(branchId, KitchenEventType.CHANGED, kitchenOrder)
        storefrontOrderBus.publishAfterCommit(order, items)
        order.tableId?.let { tableGateway.tableChanged(branchId, it) }
        log.info("preparation status changed orderId={} branchId={} status={}", order.orderId, branchId, next)

        if (next == PreparationStatus.READY) {
            order.linkedShopperId()?.let { shopperId ->
                eventPublisher.publishEvent(
                    OrderReadyForPickup(order.orderId, order.id, order.merchantBranchId, order.pickupNumber, shopperId, order.fulfilmentType, order.tableId != null)
                )
            }
        }
        return kitchenOrder
    }

    /** Configured stations plus historical active routing, so removing a catalog station cannot hide its work. */
    @Transactional(readOnly = true)
    fun stations(branchId: UUID): List<PreparationStation> {
        val configured = merchantGateway.findPreparationStations(branchId)
        val snapshots = listOpenOrders(branchId).flatMap { it.items }.map { PreparationStation(it.stationId, it.stationName) }
        // Snapshots keep today's tickets of removed/renamed stations (and pre-default "Unassigned" items) reachable.
        return (configured + snapshots).distinctBy { it.id }
            .ifEmpty { listOf(PreparationStation(UUID(0, 0), "Kitchen")) }
    }

    @Transactional(readOnly = true)
    fun logoUrl(branchId: UUID): String? = merchantGateway.findBranchLogoUrl(branchId)

    @Transactional(readOnly = true)
    fun requireStation(branchId: UUID, stationId: UUID) {
        if (stations(branchId).none { it.id == stationId }) throw NoSuchElementException("Station not found in this branch")
    }

    @Transactional(readOnly = true)
    fun listStationOrders(branchId: UUID, stationId: UUID): List<KitchenOrder> {
        requireStation(branchId, stationId)
        return listOpenOrders(branchId).map { it.forStation(stationId) }
            .filter { it.items.isNotEmpty() && (it.cancelled || it.preparationStatus != PreparationStatus.READY) }
    }

    fun updateStationPreparationStatus(branchId: UUID, orderId: UUID, stationId: UUID, next: PreparationStatus): KitchenOrder {
        // Serialize all station changes and item mutations on the same customer Order.
        val order = orderRepository.findByIdForPreparationUpdate(orderId)
            ?.takeIf { it.merchantBranchId == branchId }
            ?: throw NoSuchElementException("Order not found")
        if (order.status == OrderStatus.CANCELLED) throw OrderMutationConflictException("Order is cancelled")
        val items = orderItemRepository.findAllByOrderIdFk(order.id)
        val ticket = order.toKitchenOrder(items).forStation(stationId)
        if (ticket.items.isEmpty()) throw NoSuchElementException("Station ticket not found")
        if (next != PreparationStatus.RECEIVED && ticket.preparationStatus == next) return ticket
        if (!ticket.preparationStatus.canAdvanceTo(next)) {
            throw OrderMutationConflictException("Preparation status cannot change from ${ticket.preparationStatus} to $next")
        }
        val selected = items.filter { it.preparationStationId == stationId && !it.locked && it.preparationStatus != PreparationStatus.READY }
        selected.forEach {
            it.preparationStatus = next
            if (next == PreparationStatus.READY) it.locked = true
        }
        orderItemRepository.saveAll(selected)
        val previous = order.preparationStatus
        order.preparationStatus = when {
            items.all { it.locked || it.preparationStatus == PreparationStatus.READY } -> PreparationStatus.READY
            items.any { it.locked || it.preparationStatus != PreparationStatus.RECEIVED } -> PreparationStatus.PREPARING
            else -> PreparationStatus.RECEIVED
        }
        order.updatedAt = java.time.Instant.now()
        orderRepository.save(order)
        val updated = order.toKitchenOrder(items)
        kitchenOrderBus.publishAfterCommit(branchId, KitchenEventType.CHANGED, updated)
        storefrontOrderBus.publishAfterCommit(order, items)
        order.tableId?.let { tableGateway.tableChanged(branchId, it) }
        if (previous != PreparationStatus.READY && order.preparationStatus == PreparationStatus.READY) {
            order.linkedShopperId()?.let { shopperId ->
                eventPublisher.publishEvent(OrderReadyForPickup(order.orderId, order.id, branchId, order.pickupNumber, shopperId, order.fulfilmentType, order.tableId != null))
            }
        }
        return updated.forStation(stationId)
    }

    private fun Order.isOnKitchenScreen() =
        appointmentId == null && preparationStatus != PreparationStatus.READY

    /** The Target Shopper of an Invoice Order, otherwise the shopper who placed the order. */
    private fun Order.linkedShopperId(): String? =
        targetShopperId ?: createdById.takeIf { creatorType == CreatorType.SHOPPER }
}
