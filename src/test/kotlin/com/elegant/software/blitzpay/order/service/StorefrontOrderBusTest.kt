package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.PreparationStatus
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StorefrontOrderBusTest {
    private val bus = StorefrontOrderBus()

    /** Records what would be written to the wire; optionally fails like a disconnected client. */
    private class RecordingEmitter(private val fail: Boolean = false) : SseEmitter() {
        val sent = mutableListOf<String>()
        override fun send(builder: SseEventBuilder) {
            if (fail) throw IOException("client gone")
            sent += builder.build().joinToString("") { it.data.toString() }
        }
    }

    private fun order(orderId: String = "ORD-GUEST0001", creatorType: CreatorType = CreatorType.GUEST) = Order(
        orderId = orderId,
        merchantApplicationId = UUID.randomUUID(),
        merchantBranchId = UUID.randomUUID(),
        creatorType = creatorType,
        createdById = "guest",
        currency = "EUR",
        totalAmountMinor = 100,
        itemCount = 1,
        preparationStatus = PreparationStatus.PREPARING,
        pickupNumber = 7,
    )

    @AfterEach
    fun clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization()
    }

    @Test
    fun `publishes order changed with the guest order to that order's subscribers only`() {
        val mine = RecordingEmitter()
        val other = RecordingEmitter()
        bus.register("ORD-GUEST0001", mine)
        bus.register("ORD-OTHER0001", other)

        bus.publishAfterCommit(order(), emptyList())

        assertEquals(1, mine.sent.size)
        val wire = mine.sent.single()
        assertTrue("id:ORD-GUEST0001" in wire, wire)
        assertTrue("event:order.changed" in wire, wire)
        assertTrue("PREPARING" in wire && "pickupNumber" in wire, wire)
        assertTrue(other.sent.isEmpty())
    }

    @Test
    fun `waits for the transaction to commit`() {
        val emitter = RecordingEmitter()
        bus.register("ORD-GUEST0001", emitter)
        TransactionSynchronizationManager.initSynchronization()

        bus.publishAfterCommit(order(), emptyList())
        assertTrue(emitter.sent.isEmpty())

        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        assertEquals(1, emitter.sent.size)
    }

    @Test
    fun `ignores non-guest orders`() {
        val emitter = RecordingEmitter()
        bus.register("ORD-SHOPPER01", emitter)

        bus.publishAfterCommit(order("ORD-SHOPPER01", CreatorType.SHOPPER), emptyList())

        assertTrue(emitter.sent.isEmpty())
    }

    @Test
    fun `heartbeat drops dead emitters and forgets orders without subscribers`() {
        val alive = RecordingEmitter()
        bus.register("ORD-GUEST0001", alive)
        bus.register("ORD-GUEST0001", RecordingEmitter(fail = true))
        bus.register("ORD-GONE00001", RecordingEmitter(fail = true))

        bus.heartbeat()

        assertEquals(1, alive.sent.size)
        assertTrue(":heartbeat" in alive.sent.single())
        assertEquals(1, bus.subscriberCount("ORD-GUEST0001"))
        assertEquals(0, bus.subscriberCount("ORD-GONE00001"))
    }
}
