package com.elegant.software.blitzpay.order.persistence.repository

import com.elegant.software.blitzpay.order.persistence.model.PaymentAttempt
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PaymentAttemptRepository : JpaRepository<PaymentAttempt, UUID> {
    fun findByPaymentRequestId(paymentRequestId: UUID): PaymentAttempt?
    fun deleteAllByOrderIdFk(orderIdFk: UUID)
    fun countByOrderId(orderId: String): Long
}
