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
    val orderType: OrderType,
    val shopperFirstName: String? = null,
    val receivedAt: Instant,
    val preparationStatus: PreparationStatus,
    val cancelled: Boolean,
    val items: List<KitchenOrderItem>,
)

data class KitchenOrderItem(
    val itemId: UUID,
    val name: String,
    val quantity: Int,
    val locked: Boolean,
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
    orderType = orderType,
    shopperFirstName = buyerContactName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() },
    receivedAt = createdAt,
    preparationStatus = preparationStatus,
    cancelled = status == OrderStatus.CANCELLED,
    items = items.sortedBy { it.createdAt }.map { KitchenOrderItem(it.id, it.productName, it.quantity, it.locked) },
)
