package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.TableOrderSnapshot
import com.elegant.software.blitzpay.merchant.api.TableStatus
import java.time.Duration
import java.time.Instant

/**
 * Table Status is always derived on demand: a Table is Occupied while it has an order placed, or a Guest Count set,
 * after staff last cleared it and within the auto-clear window. RESERVED is reserved for Reservations and never
 * derived yet.
 */
object TableOccupancy {

    data class Occupancy(
        val status: TableStatus,
        val occupiedSince: Instant?,
        val orders: List<TableOrderSnapshot>,
        val guestCount: Int? = null,
    )

    /** Orders placed after this instant count towards occupancy. */
    fun windowStart(clearedAt: Instant?, now: Instant, autoClear: Duration): Instant {
        val autoClearStart = now.minus(autoClear)
        return if (clearedAt != null && clearedAt.isAfter(autoClearStart)) clearedAt else autoClearStart
    }

    /**
     * Occupied when an order was placed, or staff/the first shopper set a Guest Count, after the window start.
     * The Guest Count belongs to the current sitting: it counts when set inside the window, or when it was set no more
     * than the auto-clear period before the sitting's first current order (a long sitting kept alive by new orders).
     */
    fun derive(
        clearedAt: Instant?,
        ordersAtTable: List<TableOrderSnapshot>,
        now: Instant,
        autoClear: Duration,
        guestCount: Int? = null,
        guestsSetAt: Instant? = null,
    ): Occupancy {
        val start = windowStart(clearedAt, now, autoClear)
        val current = ordersAtTable.filter { it.createdAt.isAfter(start) }.sortedBy { it.createdAt }
        val guestsAt = guestsSetAt?.takeIf { guestCount != null && (clearedAt == null || it.isAfter(clearedAt)) }
        val guestsCurrent = guestsAt != null && (
            guestsAt.isAfter(start) ||
                (current.isNotEmpty() && !guestsAt.isBefore(current.first().createdAt.minus(autoClear)))
            )
        if (current.isEmpty() && !guestsCurrent) return Occupancy(TableStatus.FREE, null, emptyList())
        val since = listOfNotNull(current.firstOrNull()?.createdAt, guestsAt?.takeIf { guestsCurrent }).min()
        return Occupancy(TableStatus.OCCUPIED, since, current, guestCount.takeIf { guestsCurrent })
    }
}
