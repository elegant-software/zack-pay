package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.TableLiveResponse
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

/**
 * Per-Branch SSE fan-out of the live floor, the same shape as the Kitchen Screen's `KitchenOrderBus`. Every event is
 * `table` carrying the changed Table's [TableLiveResponse]. In-memory: clients reload `/floor` on reconnect.
 */
@Component
class TableFloorBus {
    private val log = LoggerFactory.getLogger(TableFloorBus::class.java)
    private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>()

    fun register(branchId: UUID, emitter: SseEmitter) {
        emitters.computeIfAbsent(branchId) { CopyOnWriteArrayList() }.add(emitter)
        emitter.onCompletion { remove(branchId, emitter) }
        emitter.onTimeout { remove(branchId, emitter) }
        emitter.onError { remove(branchId, emitter) }
    }

    fun hasListeners(branchId: UUID): Boolean = !emitters[branchId].isNullOrEmpty()

    /**
     * Emits once the current transaction commits (immediately when none is active), so floors never show rolled-back
     * changes. [table] is only evaluated when someone is listening, so changes on unwatched Branches cost nothing.
     */
    fun publishAfterCommit(branchId: UUID, table: () -> TableLiveResponse?) {
        if (!hasListeners(branchId)) return
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = emitSafely(branchId, table)
            })
        } else {
            emitSafely(branchId, table)
        }
    }

    fun emit(branchId: UUID, table: TableLiveResponse) {
        val list = emitters[branchId] ?: return
        send(branchId, list) { tableEvent(table) }
    }

    /** SSE comment line every 15 s keeps idle connections open through proxies and prunes dead emitters. */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    fun heartbeat() {
        emitters.forEach { (branchId, list) -> send(branchId, list) { SseEmitter.event().comment("heartbeat") } }
    }

    private fun emitSafely(branchId: UUID, table: () -> TableLiveResponse?) {
        // A failure to build the event must never surface in the caller that changed the order or Table.
        runCatching { table() }
            .onFailure { log.warn("live floor event skipped branchId={}", branchId, it) }
            .getOrNull()
            ?.let { emit(branchId, it) }
    }

    private fun send(branchId: UUID, list: List<SseEmitter>, event: () -> SseEmitter.SseEventBuilder) {
        val dead = list.filter { emitter -> runCatching { emitter.send(event()) }.isFailure }
        if (dead.isNotEmpty()) log.debug("dropping {} dead floor emitters branchId={}", dead.size, branchId)
        dead.forEach { remove(branchId, it) }
    }

    private fun remove(branchId: UUID, emitter: SseEmitter) {
        emitters[branchId]?.remove(emitter)
    }

    companion object {
        const val EVENT_NAME = "table"
        const val HEARTBEAT_INTERVAL_MS = 15_000L

        fun tableEvent(table: TableLiveResponse): SseEmitter.SseEventBuilder =
            SseEmitter.event().id(table.table.id.toString()).name(EVENT_NAME).data(table, MediaType.APPLICATION_JSON)
    }
}
