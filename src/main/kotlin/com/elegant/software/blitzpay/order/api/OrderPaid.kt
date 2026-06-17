package com.elegant.software.blitzpay.order.api

import java.time.Instant
import java.util.UUID

data class OrderPaid(
    val orderId: String,
    val orderIdFk: UUID,
    val merchantId: UUID,
    val currency: String,
    val totalAmountMinor: Long,
    val buyerContactName: String?,
    val buyerContactStreet: String?,
    val buyerContactZip: String?,
    val buyerContactCity: String?,
    val buyerContactCountry: String?,
    val paidAt: Instant,
    val items: List<OrderItemSnapshot> = emptyList(),
)

data class OrderItemSnapshot(
    val productName: String,
    val quantity: Int,
    val effectiveUnitPriceMinor: Long,
)
