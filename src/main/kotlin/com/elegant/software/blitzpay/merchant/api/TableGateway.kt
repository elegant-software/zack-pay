package com.elegant.software.blitzpay.merchant.api

import java.time.Instant
import java.util.UUID

/** Table lookups for other modules (order creation at a Table). Implemented by the merchant module. */
interface TableGateway {
    /** The Table with this Table Code (case-insensitive), or null when no Table has it. */
    fun findTableByCode(tableCode: String): TableInfo?

    /** Sets the Table's Guest Count from the first order of a sitting; ignored when the Table already has one. */
    fun seatGuestsIfUnset(tableId: UUID, guestCount: Int)

    /** Something about the Table changed (one of its orders was created, paid or prepared): updates live floors. */
    fun tableChanged(branchId: UUID, tableId: UUID)
}

data class TableInfo(
    val tableId: UUID,
    val branchId: UUID,
    val name: String,
    val active: Boolean,
)

/**
 * Orders placed at Tables, needed to derive Table Status. Declared here and implemented by the order module,
 * so the merchant module never depends on order (order already depends on merchant).
 */
interface TableOrderLookup {
    /** Non-cancelled orders placed at any of [tableIds] after [placedAfter]. */
    fun findOrdersAtTables(tableIds: Collection<UUID>, placedAfter: Instant): List<TableOrderSnapshot>

    /** Whether any order was ever placed at the Table (such a Table may only be deactivated, not deleted). */
    fun hasOrders(tableId: UUID): Boolean
}

data class TableOrderSnapshot(
    val tableId: UUID,
    val orderId: String,
    val pickupNumber: Int?,
    val totalAmountMinor: Long,
    val currency: String,
    val paymentStatus: String,
    val preparationStatus: String,
    val createdAt: Instant,
)
