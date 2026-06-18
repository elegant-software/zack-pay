package com.elegant.software.blitzpay.order.api

import java.time.LocalDate
import java.util.UUID

data class InvoiceOrderCreated(
    val orderId: String,
    val orderIdFk: UUID,
    val merchantId: UUID,
    val branchId: UUID?,
    val targetShopperId: String,
    val totalAmountMinor: Long,
    val currency: String,
    val dueDate: LocalDate?,
    val buyerContactName: String?,
    val buyerContactStreet: String?,
    val buyerContactZip: String?,
    val buyerContactCity: String?,
    val buyerContactCountry: String?,
    val items: List<OrderItemSnapshot> = emptyList(),
)
