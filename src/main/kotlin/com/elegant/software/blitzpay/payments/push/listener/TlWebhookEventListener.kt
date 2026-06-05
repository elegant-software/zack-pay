package com.elegant.software.blitzpay.payments.push.listener

import com.elegant.software.blitzpay.payments.truelayer.inbound.TlWebhookEnvelope
import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.order.api.PaymentStatusChanged
import com.elegant.software.blitzpay.order.api.PaymentStatusCode
import com.elegant.software.blitzpay.payments.push.persistence.model.ProcessedWebhookEventEntity
import com.elegant.software.blitzpay.payments.push.persistence.model.ProcessedWebhookEventRepository
import com.elegant.software.blitzpay.payments.push.service.PaymentStatusService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
class TlWebhookEventListener(
    private val processedRepository: ProcessedWebhookEventRepository,
    private val paymentStatusService: PaymentStatusService,
    private val publisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(TlWebhookEventListener::class.java)

    @Transactional
    @EventListener
    fun on(envelope: TlWebhookEnvelope) {
        val eventId = envelope.event_id
        if (eventId.isNullOrBlank()) {
            log.warn("webhook without event_id; ignoring")
            return
        }
        val newStatus = mapStatus(envelope.type) ?: run {
            log.debug("webhook type={} does not map to a status; skipping", envelope.type)
            return
        }
        val paymentRequestId = envelope.metadata?.get("paymentRequestId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (paymentRequestId == null) {
            log.warn("webhook missing paymentRequestId metadata event={}", eventId)
            return
        }

        LogContext.with(
            LogContext.EVENT_ID to eventId,
            LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
            LogContext.PROVIDER to "TRUELAYER",
        ) {
            log.info("truelayer webhook mapped status={} type={}", newStatus, envelope.type)
            if (processedRepository.existsById(eventId)) {
                log.info("webhook duplicate event={}; skipping", eventId)
                return
            }
            processedRepository.save(ProcessedWebhookEventEntity(eventId = eventId))
            log.info("truelayer webhook marked event as processed")

            val occurredAt = runCatching { envelope.timestamp?.let(Instant::parse) }.getOrNull() ?: Instant.now()
            val transition = paymentStatusService.apply(paymentRequestId, newStatus, occurredAt, eventId)
            if (transition.changed) {
                log.info(
                    "truelayer webhook publishing payment status change previousStatus={} newStatus={}",
                    transition.previousStatus, transition.newStatus,
                )
                publisher.publishEvent(
                    PaymentStatusChanged(
                        paymentRequestId = transition.paymentRequestId,
                        orderId = transition.orderId,
                        newStatus = transition.newStatus,
                        previousStatus = transition.previousStatus,
                        occurredAt = transition.occurredAt,
                        sourceEventId = transition.sourceEventId,
                    )
                )
            } else {
                log.info("truelayer webhook status unchanged currentStatus={}", transition.newStatus)
            }
        }
    }

    private fun mapStatus(type: String): PaymentStatusCode? = when (type) {
        "payment_executed" -> PaymentStatusCode.EXECUTED
        "payment_settled" -> PaymentStatusCode.SETTLED
        "payment_failed" -> PaymentStatusCode.FAILED
        "payment_expired" -> PaymentStatusCode.EXPIRED
        else -> null
    }
}
