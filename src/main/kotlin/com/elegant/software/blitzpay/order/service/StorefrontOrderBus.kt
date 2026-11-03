package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.api.toResponse
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderItem
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Per-Order SSE fan-out for the web storefront, the same shape as [KitchenOrderBus]. Every event is `order.changed`
 * carrying the full guest [OrderResponse]. In-memory: clients re-sync on reconnect (the stream sends a snapshot first).
 */
@Component
class StorefrontOrderBus {
    private val log = LoggerFactory.getLogger(StorefrontOrderBus::class.java)
    private val emitters = ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>()

    fun register(orderId: String, emitter: SseEmitter) {
        emitters.computeIfAbsent(orderId) { CopyOnWriteArrayList() }.add(emitter)
        emitter.onCompletion { remove(orderId, emitter) }
        emitter.onTimeout { remove(orderId, emitter) }
        emitter.onError { remove(orderId, emitter) }
    }

    /** Guest orders only; emits once the current transaction commits (immediately when none is active). */
    fun publishAfterCommit(order: Order, items: List<OrderItem>) {
        if (order.creatorType != CreatorType.GUEST) return
        val response = order.toResponse(items)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = emit(response)
            })
        } else {
            emit(response)
        }
    }

    fun emit(order: OrderResponse) {
        val list = emitters[order.orderId] ?: return
        send(order.orderId, list) { changedEvent(order) }
    }

    /** SSE comment line every 15 s keeps idle connections open through proxies and prunes dead emitters. */
    @Scheduled(fixedRate = KitchenOrderBus.HEARTBEAT_INTERVAL_MS)
    fun heartbeat() {
        emitters.forEach { (orderId, list) -> send(orderId, list) { SseEmitter.event().comment("heartbeat") } }
    }

    private fun send(orderId: String, list: List<SseEmitter>, event: () -> SseEmitter.SseEventBuilder) {
        val dead = list.filter { emitter -> runCatching { emitter.send(event()) }.isFailure }
        if (dead.isNotEmpty()) log.debug("dropping {} dead storefront emitters orderId={}", dead.size, orderId)
        dead.forEach { remove(orderId, it) }
    }

    // Unlike Branches, Orders are unbounded: drop the key once its last emitter is gone.
    private fun remove(orderId: String, emitter: SseEmitter) {
        emitters.computeIfPresent(orderId) { _, list -> list.apply { remove(emitter) }.ifEmpty { null } }
    }

    internal fun subscriberCount(orderId: String): Int = emitters[orderId]?.size ?: 0

    companion object {
        const val EVENT_NAME = "order.changed"

        fun changedEvent(order: OrderResponse): SseEmitter.SseEventBuilder =
            SseEmitter.event().id(order.orderId).name(EVENT_NAME).data(order, MediaType.APPLICATION_JSON)
    }
}
