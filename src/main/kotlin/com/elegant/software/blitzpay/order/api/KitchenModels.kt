package com.elegant.software.blitzpay.order.api

import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderItem
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.OrderType
import com.elegant.software.blitzpay.order.persistence.model.PreparationStatus
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.util.UUID

/** An Order as the Kitchen Screen shows it. `orderId`/`itemId` are opaque; staff see `pickupNumber` and `orderCode` (ADR-0012). */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class KitchenOrder(
    val orderId: UUID,
    val orderCode: String,
    val pickupNumber: Int?,
    /** How the order is consumed; absent for invoice and appointment orders. */
    val fulfilmentType: FulfilmentType? = null,
    val orderType: OrderType,
    val shopperFirstName: String? = null,
    val receivedAt: Instant,
    val preparationStatus: PreparationStatus,
    val cancelled: Boolean,
    val items: List<KitchenOrderItem>,
    /** The Table to bring the order to; absent for counter pickup. */
    val tableId: UUID? = null,
    val tableName: String? = null,
)

data class KitchenOrderItem(
    val itemId: UUID,
    val name: String,
    val quantity: Int,
    val locked: Boolean,
    val stationId: UUID = UUID(0, 0),
    val stationName: String = "Unassigned",
    val preparationStatus: PreparationStatus = PreparationStatus.RECEIVED,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class UpdatePreparationStatusRequest(
    val status: PreparationStatus,
)

/** SSE event names of the Kitchen Screen stream. */
enum class KitchenEventType(val eventName: String) {
    CREATED("order.created"),
    CHANGED("order.changed"),
    CANCELLED("order.cancelled"),
}

internal fun Order.toKitchenOrder(items: List<OrderItem>) = KitchenOrder(
    orderId = id,
    orderCode = orderId,
    pickupNumber = pickupNumber,
    fulfilmentType = fulfilmentType,
    orderType = orderType,
    shopperFirstName = buyerContactName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() },
    receivedAt = createdAt,
    preparationStatus = preparationStatus,
    cancelled = status == OrderStatus.CANCELLED,
    items = items.sortedBy { it.createdAt }.map { KitchenOrderItem(it.id, it.productName, it.quantity, it.locked, it.preparationStationId, it.preparationStationName, if (it.locked) PreparationStatus.READY else it.preparationStatus) },
    tableId = tableId,
    tableName = tableName,
)

/** A station ticket keeps the customer order identity and table, but owns only its preparation work. */
fun KitchenOrder.forStation(stationId: UUID): KitchenOrder {
    val selected = items.filter { it.stationId == stationId }
    val progress = when {
        selected.isEmpty() || selected.all { it.locked || it.preparationStatus == PreparationStatus.READY } -> PreparationStatus.READY
        selected.any { it.preparationStatus != PreparationStatus.RECEIVED || it.locked } -> PreparationStatus.PREPARING
        else -> PreparationStatus.RECEIVED
    }
    return copy(items = selected, preparationStatus = progress, cancelled = cancelled && selected.isNotEmpty())
}
