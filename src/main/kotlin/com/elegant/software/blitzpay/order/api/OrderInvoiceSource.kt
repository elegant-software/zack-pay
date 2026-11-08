package com.elegant.software.blitzpay.order.api

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Everything the invoice module needs to render and deliver the invoice of one order.
 * [issuedAt] is `paidAt` for paid orders and `createdAt` for merchant-issued invoice orders.
 */
data class OrderInvoiceSource(
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
    val issuedAt: Instant,
    val dueDate: LocalDate?,
    val isInvoiceOrder: Boolean,
    val items: List<OrderItemSnapshot> = emptyList(),
    val buyerEmail: String? = null,
    /** Anonymous storefront order; its [buyerEmail] (if any) is the guest's consented invoice email. */
    val isGuestOrder: Boolean = false,
)

data class OrderItemSnapshot(
    val productName: String,
    val quantity: Int,
    val effectiveUnitPriceMinor: Long,
)
