package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.order.api.KitchenEventType
import com.elegant.software.blitzpay.order.api.KitchenOrder
import com.elegant.software.blitzpay.order.api.forStation
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Per-Branch SSE fan-out for Kitchen Screens, following `PaymentUpdateBus`. In-memory: clients re-sync via the list endpoint on reconnect. */
@Component
class KitchenOrderBus {
    private val log = LoggerFactory.getLogger(KitchenOrderBus::class.java)
    private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>()

    private val stationFilters = ConcurrentHashMap<SseEmitter, UUID>()

    fun register(branchId: UUID, emitter: SseEmitter, stationId: UUID? = null) {
        if (stationId != null) stationFilters[emitter] = stationId
        emitters.computeIfAbsent(branchId) { CopyOnWriteArrayList() }.add(emitter)
        emitter.onCompletion { remove(branchId, emitter) }
        emitter.onTimeout { remove(branchId, emitter) }
        emitter.onError { remove(branchId, emitter) }
    }

    /** Emits once the current transaction commits (immediately when none is active), so screens never see rolled-back changes. */
    fun publishAfterCommit(branchId: UUID?, type: KitchenEventType, order: KitchenOrder) {
        if (branchId == null) return
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = emit(branchId, type, order)
            })
        } else {
            emit(branchId, type, order)
        }
    }

    fun emit(branchId: UUID, type: KitchenEventType, order: KitchenOrder) {
        val list = emitters[branchId] ?: return
        send(branchId, list) { emitter ->
            val ticket = stationFilters[emitter]?.let { order.forStation(it) } ?: order
            // Empty READY tickets clear work removed from a station while the display was connected.
            SseEmitter.event().id(order.orderId.toString())
                .name(if (ticket.items.isEmpty()) KitchenEventType.CHANGED.eventName else type.eventName)
                .data(ticket, MediaType.APPLICATION_JSON)
        }
    }

    /** SSE comment line every 15 s keeps idle connections open through proxies and prunes dead emitters. */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    fun heartbeat() {
        emitters.forEach { (branchId, list) -> send(branchId, list) { SseEmitter.event().comment("heartbeat") } }
    }

    private fun send(branchId: UUID, list: List<SseEmitter>, event: (SseEmitter) -> SseEmitter.SseEventBuilder) {
        val dead = list.filter { emitter -> runCatching { emitter.send(event(emitter)) }.isFailure }
        if (dead.isNotEmpty()) log.debug("dropping {} dead kitchen emitters branchId={}", dead.size, branchId)
        dead.forEach { remove(branchId, it) }
    }

    private fun remove(branchId: UUID, emitter: SseEmitter) {
        emitters[branchId]?.remove(emitter)
        stationFilters.remove(emitter)
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MS = 15_000L
    }
}
