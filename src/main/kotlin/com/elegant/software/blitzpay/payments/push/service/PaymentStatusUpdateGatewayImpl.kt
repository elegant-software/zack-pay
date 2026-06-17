package com.elegant.software.blitzpay.payments.push.service

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.order.api.PaymentStatusChanged
import com.elegant.software.blitzpay.order.api.PaymentStatusCode
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusUpdateGateway
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Component
class PaymentStatusUpdateGatewayImpl(
    private val paymentStatusService: PaymentStatusService,
    private val eventPublisher: ApplicationEventPublisher,
) : PaymentStatusUpdateGateway {
    private val log = LoggerFactory.getLogger(PaymentStatusUpdateGatewayImpl::class.java)

    @Transactional
    override fun settle(paymentRequestId: UUID, sourceEventId: String, occurredAt: Instant) =
        applyAndPublish(paymentRequestId, PaymentStatusCode.SETTLED, sourceEventId, occurredAt)

    @Transactional
    override fun fail(paymentRequestId: UUID, sourceEventId: String, occurredAt: Instant) =
        applyAndPublish(paymentRequestId, PaymentStatusCode.FAILED, sourceEventId, occurredAt)

    private fun applyAndPublish(paymentRequestId: UUID, status: PaymentStatusCode, sourceEventId: String, occurredAt: Instant) {
        LogContext.with(
            LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
            LogContext.EVENT_ID to sourceEventId,
        ) {
            log.info("apply payment status update requested status={} occurredAt={}", status, occurredAt)
            val transition = paymentStatusService.apply(paymentRequestId, status, occurredAt, sourceEventId)
            if (transition.changed) {
                log.info(
                    "publishing payment status change previousStatus={} newStatus={}",
                    transition.previousStatus, transition.newStatus,
                )
                eventPublisher.publishEvent(
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
                log.info("payment status change suppressed currentStatus={}", transition.newStatus)
            }
        }
    }
}
