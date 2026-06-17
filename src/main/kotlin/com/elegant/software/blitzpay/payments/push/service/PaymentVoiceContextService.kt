package com.elegant.software.blitzpay.payments.push.service

import com.elegant.software.blitzpay.payments.push.api.PaymentVoiceContextGateway
import com.elegant.software.blitzpay.payments.push.api.RecentPaymentSummary
import com.elegant.software.blitzpay.payments.push.persistence.model.DeviceRegistrationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class PaymentVoiceContextService(
    private val paymentStatusService: PaymentStatusService,
    private val deviceRegistrationRepository: DeviceRegistrationRepository,
) : PaymentVoiceContextGateway, com.elegant.software.blitzpay.payments.push.api.PaymentStatusInitializationGateway {
    private val log = LoggerFactory.getLogger(PaymentVoiceContextService::class.java)

    override fun findRecentPaymentsBySubject(subject: String, limit: Int): List<RecentPaymentSummary> =
        paymentStatusService.findRecentBySubject(subject, limit)

    override fun initialize(
        paymentRequestId: UUID,
        payerRef: String?,
        orderId: String?,
        amountMinorUnits: Long?,
        currency: String?,
    ) {
        paymentStatusService.initialize(
            paymentRequestId = paymentRequestId,
            payerRef = payerRef,
            orderId = orderId,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
        )
        orderId
            ?.takeIf { it.isNotBlank() }
            ?.let { registeredOrderId ->
                val registeredDeviceCount = deviceRegistrationRepository.countByOrderIdAndInvalidFalse(registeredOrderId)
                if (registeredDeviceCount == 0L) {
                    log.info(
                        "push registration missing for orderId={} paymentRequestId={} activeDeviceCount=0",
                        registeredOrderId,
                        paymentRequestId,
                    )
                } else {
                    log.info(
                        "push registration found for orderId={} paymentRequestId={} activeDeviceCount={}",
                        registeredOrderId,
                        paymentRequestId,
                        registeredDeviceCount,
                    )
                }
            }
    }
}
