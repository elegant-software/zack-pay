package com.elegant.software.blitzpay.order.repository

import com.elegant.software.blitzpay.order.domain.PaymentSettlement
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.context.annotation.Profile
import java.util.UUID

@Profile("!contract-test")
interface PaymentSettlementRepository : JpaRepository<PaymentSettlement, UUID> {
    fun existsByPaymentRequestId(paymentRequestId: UUID): Boolean
    fun findByPaymentRequestId(paymentRequestId: UUID): PaymentSettlement?
    fun findAllByPaymentRequestIdIn(paymentRequestIds: Set<UUID>): List<PaymentSettlement>
}
