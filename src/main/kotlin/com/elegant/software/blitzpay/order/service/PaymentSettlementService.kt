package com.elegant.software.blitzpay.order.application

import com.elegant.software.blitzpay.order.api.PaymentSettlementData
import com.elegant.software.blitzpay.order.api.PaymentSettlementGateway
import com.elegant.software.blitzpay.order.api.PaymentSettlementRefreshCandidate
import com.elegant.software.blitzpay.order.domain.PaymentSettlement
import com.elegant.software.blitzpay.order.repository.OrderRepository
import com.elegant.software.blitzpay.order.repository.PaymentAttemptRepository
import com.elegant.software.blitzpay.order.repository.PaymentSettlementRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Profile("!contract-test")
@Transactional
class PaymentSettlementService(
    private val orderRepository: OrderRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
    private val paymentSettlementRepository: PaymentSettlementRepository,
) : PaymentSettlementGateway {

    private val log = LoggerFactory.getLogger(PaymentSettlementService::class.java)

    override fun record(data: PaymentSettlementData) {
        if (paymentSettlementRepository.existsByPaymentRequestId(data.paymentRequestId)) {
            log.info(
                "payment settlement already exists for paymentRequestId={} — skipping",
                data.paymentRequestId,
            )
            return
        }

        val attempt = paymentAttemptRepository.findByPaymentRequestId(data.paymentRequestId)
        if (attempt == null) {
            log.warn(
                "payment settlement skipped — no PaymentAttempt found for paymentRequestId={}",
                data.paymentRequestId,
            )
            return
        }

        val settlement = PaymentSettlement(
            paymentRequestId = data.paymentRequestId,
            orderIdFk = attempt.orderIdFk,
            orderId = attempt.orderId,
            provider = data.provider,
            currency = data.currency,
            grossAmountMinor = data.grossAmountMinor,
            processingFeeMinor = data.processingFeeMinor,
            platformFeeMinor = data.platformFeeMinor,
            netAmountMinor = data.netAmountMinor,
            paymentMethod = data.paymentMethod,
            paymentMethodDetail = data.paymentMethodDetail,
            fundsAvailableAt = data.fundsAvailableAt,
            providerPaymentReference = data.providerPaymentReference,
            providerChargeReference = data.providerChargeReference,
            providerBalanceTxReference = data.providerBalanceTxReference,
            providerDashboardUrl = data.providerDashboardUrl,
            settledAt = data.settledAt,
        )
        paymentSettlementRepository.save(settlement)
        log.info(
            "payment settlement recorded paymentRequestId={} provider={} grossAmountMinor={} netAmountMinor={} paymentMethod={}",
            data.paymentRequestId, data.provider, data.grossAmountMinor, data.netAmountMinor, data.paymentMethod,
        )
    }

    @Transactional(readOnly = true)
    override fun findStripeRefreshCandidates(
        merchantId: UUID,
        paymentRequestIds: Set<UUID>,
    ): List<PaymentSettlementRefreshCandidate> =
        paymentSettlementRepository.findAllByPaymentRequestIdIn(paymentRequestIds)
            .filter { it.provider == "STRIPE" }
            .mapNotNull { settlement ->
                val order = orderRepository.findById(settlement.orderIdFk).orElse(null)
                    ?: return@mapNotNull null
                if (order.merchantApplicationId != merchantId) return@mapNotNull null
                PaymentSettlementRefreshCandidate(
                    paymentRequestId = settlement.paymentRequestId,
                    merchantId = order.merchantApplicationId,
                    currency = settlement.currency,
                    grossAmountMinor = settlement.grossAmountMinor,
                    platformFeeMinor = settlement.platformFeeMinor,
                    providerPaymentReference = settlement.providerPaymentReference,
                    providerChargeReference = settlement.providerChargeReference,
                    settledAt = settlement.settledAt,
                )
            }

    override fun refresh(data: PaymentSettlementData): Boolean {
        val settlement = paymentSettlementRepository.findByPaymentRequestId(data.paymentRequestId)
            ?: return false
        if (settlement.provider != "STRIPE") return false
        settlement.refreshFrom(data)
        paymentSettlementRepository.save(settlement)
        log.info(
            "payment settlement refreshed paymentRequestId={} processingFeeMinor={} netAmountMinor={} balanceTransaction={}",
            data.paymentRequestId, data.processingFeeMinor, data.netAmountMinor, data.providerBalanceTxReference,
        )
        return true
    }
}
