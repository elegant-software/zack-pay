package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.TableLiveResponse
import com.elegant.software.blitzpay.merchant.api.TableResponse
import com.elegant.software.blitzpay.merchant.api.TableStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableFloorBusTest {
    private val bus = TableFloorBus()
    private val branchId = UUID.randomUUID()

    /** Records what would be written to the wire; optionally fails like a disconnected client. */
    private class RecordingEmitter(private val fail: Boolean = false) : SseEmitter() {
        val sent = mutableListOf<String>()
        override fun send(builder: SseEventBuilder) {
            if (fail) throw IOException("client gone")
            sent += builder.build().joinToString("") { it.data.toString() }
        }
    }

    private val table = TableLiveResponse(
        table = TableResponse(UUID.randomUUID(), "K7M2QX", "5", true, "https://shop.example/t/K7M2QX", null, 4),
        status = TableStatus.OCCUPIED,
        occupiedSince = Instant.parse("2026-11-04T11:00:00Z"),
        orders = emptyList(),
        guestCount = 3,
    )

    @AfterEach
    fun clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization()
    }

    @Test
    fun `publishes a table event to the Branch's floors only`() {
        val mine = RecordingEmitter()
        val other = RecordingEmitter()
        bus.register(branchId, mine)
        bus.register(UUID.randomUUID(), other)

        bus.publishAfterCommit(branchId) { table }

        val wire = mine.sent.single()
        assertTrue("id:${table.table.id}" in wire, wire)
        assertTrue("event:table" in wire, wire)
        assertTrue("guestCount=3" in wire && "OCCUPIED" in wire, wire)
        assertTrue(other.sent.isEmpty())
    }

    @Test
    fun `does not build the event when nobody watches the Branch`() {
        var built = false
        bus.publishAfterCommit(branchId) { built = true; table }
        assertFalse(built)
    }

    @Test
    fun `waits for the transaction to commit`() {
        val mine = RecordingEmitter()
        bus.register(branchId, mine)
        TransactionSynchronizationManager.initSynchronization()

        bus.publishAfterCommit(branchId) { table }
        assertTrue(mine.sent.isEmpty())
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

        assertEquals(1, mine.sent.size)
    }

    @Test
    fun `a failing event builder or dead client never breaks the caller`() {
        val dead = RecordingEmitter(fail = true)
        bus.register(branchId, dead)
        bus.publishAfterCommit(branchId) { error("boom") }
        bus.publishAfterCommit(branchId) { table }
        assertFalse(bus.hasListeners(branchId))
    }
}
