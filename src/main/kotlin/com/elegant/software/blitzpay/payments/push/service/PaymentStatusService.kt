package com.elegant.software.blitzpay.payments.push.service

import com.elegant.software.blitzpay.order.api.PaymentStatusCode
import com.elegant.software.blitzpay.payments.push.api.RecentPaymentSummary
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusResponse
import com.elegant.software.blitzpay.payments.push.persistence.model.PaymentStatusEntity
import com.elegant.software.blitzpay.payments.push.persistence.model.PaymentStatusRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional
import java.util.UUID

data class StatusTransition(
    val paymentRequestId: UUID,
    val orderId: String?,
    val newStatus: PaymentStatusCode,
    val previousStatus: PaymentStatusCode?,
    val occurredAt: Instant,
    val sourceEventId: String,
    val changed: Boolean,
)

@Service
class PaymentStatusService(
    private val repository: PaymentStatusRepository,
) {
    private val log = LoggerFactory.getLogger(PaymentStatusService::class.java)

    fun getByRequestId(paymentRequestId: UUID): Optional<PaymentStatusResponse> =
        repository.findById(paymentRequestId).map { entity ->
            PaymentStatusResponse(
                paymentRequestId = entity.paymentRequestId,
                status = entity.currentStatus,
                terminal = entity.currentStatus.isTerminal(),
                lastEventAt = entity.lastEventAt,
            )
        }

    @Transactional
    fun initialize(
        paymentRequestId: UUID,
        payerRef: String?,
        orderId: String?,
        amountMinorUnits: Long?,
        currency: String?,
    ) {
        val existing = repository.findById(paymentRequestId).orElse(null)
        if (existing == null) {
            repository.save(
                PaymentStatusEntity(
                    paymentRequestId = paymentRequestId,
                    currentStatus = PaymentStatusCode.PENDING,
                    updatedAt = Instant.now(),
                    payerRef = payerRef,
                    orderId = orderId,
                    amountMinorUnits = amountMinorUnits,
                    currency = currency,
                )
            )
            log.info(
                "payment status record created paymentRequestId={} orderId={} status=PENDING amountMinorUnits={} currency={} payerRefPresent={}",
                paymentRequestId, orderId, amountMinorUnits, currency, !payerRef.isNullOrBlank(),
            )
            return
        }

        existing.payerRef = payerRef ?: existing.payerRef
        existing.orderId = orderId ?: existing.orderId
        existing.amountMinorUnits = amountMinorUnits ?: existing.amountMinorUnits
        existing.currency = currency ?: existing.currency
        existing.updatedAt = Instant.now()
        repository.save(existing)
        log.info(
            "payment status record updated paymentRequestId={} orderId={} currentStatus={} payerRefPresent={}",
            paymentRequestId, orderId, existing.currentStatus, !payerRef.isNullOrBlank(),
        )
    }

    fun findRecentBySubject(subject: String, limit: Int = 5): List<RecentPaymentSummary> =
        repository.findTop5ByPayerRefOrderByUpdatedAtDesc(subject)
            .take(limit)
            .map { entity ->
                RecentPaymentSummary(
                    paymentRequestId = entity.paymentRequestId,
                    status = entity.currentStatus,
                    updatedAt = entity.updatedAt,
                    orderId = entity.orderId,
                    amountMinorUnits = entity.amountMinorUnits,
                    currency = entity.currency,
                )
            }

    @Transactional
    fun apply(
        paymentRequestId: UUID,
        newStatus: PaymentStatusCode,
        occurredAt: Instant,
        sourceEventId: String,
    ): StatusTransition {
        val existing = repository.findById(paymentRequestId).orElse(null)
        if (existing == null) {
            val saved = repository.save(
                PaymentStatusEntity(
                    paymentRequestId = paymentRequestId,
                    currentStatus = newStatus,
                    lastEventId = sourceEventId,
                    lastEventAt = occurredAt,
                    updatedAt = Instant.now(),
                )
            )
            log.info("payment status created request={} status={}", paymentRequestId, saved.currentStatus)
            return StatusTransition(paymentRequestId, saved.orderId, newStatus, null, occurredAt, sourceEventId, changed = true)
        }

        val previous = existing.currentStatus
        if (newStatus.rank() < previous.rank() || (newStatus == previous)) {
            log.info("payment status unchanged request={} current={} incoming={}", paymentRequestId, previous, newStatus)
            return StatusTransition(paymentRequestId, existing.orderId, previous, previous, occurredAt, sourceEventId, changed = false)
        }

        existing.currentStatus = newStatus
        existing.lastEventId = sourceEventId
        existing.lastEventAt = occurredAt
        existing.updatedAt = Instant.now()
        repository.save(existing)
        log.info("payment status transition request={} {}->{}", paymentRequestId, previous, newStatus)
        return StatusTransition(paymentRequestId, existing.orderId, newStatus, previous, occurredAt, sourceEventId, changed = true)
    }
}
