package com.elegant.software.blitzpay.payments.support

import com.elegant.software.blitzpay.payments.truelayer.api.PaymentResult
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Component
class PaymentUpdateBus {
    private val emitters = ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>()

    fun register(paymentRequestId: UUID, emitter: SseEmitter) {
        emitters.computeIfAbsent(paymentRequestId) { CopyOnWriteArrayList() }.add(emitter)
        emitter.onCompletion { remove(paymentRequestId, emitter) }
        emitter.onTimeout { remove(paymentRequestId, emitter) }
        emitter.onError { remove(paymentRequestId, emitter) }
    }

    fun emit(paymentRequestId: UUID, update: PaymentResult) {
        val list = emitters[paymentRequestId] ?: return
        val event = SseEmitter.event()
            .id(update.paymentRequestId.toString())
            .name("payment")
            .data(update, MediaType.APPLICATION_JSON)
        val dead = mutableListOf<SseEmitter>()
        for (emitter in list) {
            try { emitter.send(event) } catch (_: Exception) { dead.add(emitter) }
        }
        dead.forEach { remove(paymentRequestId, it) }
    }

    fun complete(paymentRequestId: UUID) {
        emitters.remove(paymentRequestId)?.forEach { runCatching { it.complete() } }
    }

    private fun remove(paymentRequestId: UUID, emitter: SseEmitter) {
        emitters[paymentRequestId]?.remove(emitter)
    }
}
