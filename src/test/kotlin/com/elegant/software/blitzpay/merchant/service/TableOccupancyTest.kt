package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.TableOrderSnapshot
import com.elegant.software.blitzpay.merchant.api.TableStatus
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TableOccupancyTest {

    private val now = Instant.parse("2026-11-04T12:00:00Z")
    private val autoClear = Duration.ofHours(4)
    private val tableId = UUID.randomUUID()

    private fun order(minutesAgo: Long, id: String = "ORD-$minutesAgo") = TableOrderSnapshot(
        tableId = tableId,
        orderId = id,
        pickupNumber = 7,
        totalAmountMinor = 1250,
        currency = "EUR",
        paymentStatus = "CREATED",
        preparationStatus = "RECEIVED",
        createdAt = now.minus(Duration.ofMinutes(minutesAgo)),
    )

    @Test
    fun `a Table without orders is free`() {
        val occupancy = TableOccupancy.derive(null, emptyList(), now, autoClear)
        assertEquals(TableStatus.FREE, occupancy.status)
        assertNull(occupancy.occupiedSince)
        assertTrue(occupancy.orders.isEmpty())
    }

    @Test
    fun `an order inside the window makes the Table occupied since its first order`() {
        val occupancy = TableOccupancy.derive(null, listOf(order(10), order(45)), now, autoClear)
        assertEquals(TableStatus.OCCUPIED, occupancy.status)
        assertEquals(now.minus(Duration.ofMinutes(45)), occupancy.occupiedSince)
        assertEquals(listOf("ORD-45", "ORD-10"), occupancy.orders.map { it.orderId })
    }

    @Test
    fun `orders placed before staff cleared the Table no longer count`() {
        val clearedAt = now.minus(Duration.ofMinutes(20))
        assertEquals(TableStatus.FREE, TableOccupancy.derive(clearedAt, listOf(order(30)), now, autoClear).status)

        val occupancy = TableOccupancy.derive(clearedAt, listOf(order(30), order(5)), now, autoClear)
        assertEquals(TableStatus.OCCUPIED, occupancy.status)
        assertEquals(listOf("ORD-5"), occupancy.orders.map { it.orderId })
    }

    @Test
    fun `the Table frees itself after the auto-clear window without new orders`() {
        assertEquals(TableStatus.FREE, TableOccupancy.derive(null, listOf(order(4 * 60 + 1)), now, autoClear).status)
        assertEquals(TableStatus.OCCUPIED, TableOccupancy.derive(null, listOf(order(4 * 60 - 1)), now, autoClear).status)
    }

    @Test
    fun `a Guest Count set inside the window makes the Table occupied before any order`() {
        val occupancy = TableOccupancy.derive(null, emptyList(), now, autoClear, guestCount = 3, guestsSetAt = now.minusSeconds(600))
        assertEquals(TableStatus.OCCUPIED, occupancy.status)
        assertEquals(3, occupancy.guestCount)
        assertEquals(now.minusSeconds(600), occupancy.occupiedSince)
    }

    @Test
    fun `a Guest Count from before clearing or from an expired sitting does not count`() {
        val clearedAt = now.minusSeconds(60)
        assertEquals(TableStatus.FREE, TableOccupancy.derive(clearedAt, emptyList(), now, autoClear, 3, now.minusSeconds(600)).status)
        val old = now.minus(Duration.ofHours(5))
        val expired = TableOccupancy.derive(null, emptyList(), now, autoClear, 3, old)
        assertEquals(TableStatus.FREE, expired.status)
        assertNull(expired.guestCount)
    }

    @Test
    fun `a long sitting kept alive by orders keeps its Guest Count`() {
        val occupancy = TableOccupancy.derive(null, listOf(order(60)), now, autoClear, 4, now.minus(Duration.ofHours(4).plusMinutes(30)))
        assertEquals(TableStatus.OCCUPIED, occupancy.status)
        assertEquals(4, occupancy.guestCount)
    }

    @Test
    fun `the window starts at the later of cleared_at and the auto-clear start`() {
        val oldClear = now.minus(Duration.ofHours(10))
        assertEquals(now.minus(autoClear), TableOccupancy.windowStart(oldClear, now, autoClear))
        val recentClear = now.minus(Duration.ofMinutes(5))
        assertEquals(recentClear, TableOccupancy.windowStart(recentClear, now, autoClear))
        assertEquals(now.minus(autoClear), TableOccupancy.windowStart(null, now, autoClear))
    }
}
