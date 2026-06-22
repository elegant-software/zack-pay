package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.order.api.CheckoutPaymentMethod
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderPaymentSummary
import com.elegant.software.blitzpay.order.api.PaymentRequestResolver
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.PaymentAttempt
import com.elegant.software.blitzpay.order.persistence.repository.OrderRepository
import com.elegant.software.blitzpay.order.persistence.repository.PaymentAttemptRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class PaymentAttemptService(
    private val orderRepository: OrderRepository,
    private val paymentAttemptRepository: PaymentAttemptRepository,
) : OrderGateway, PaymentRequestResolver {
    private val log = LoggerFactory.getLogger(PaymentAttemptService::class.java)

    override fun assertPayable(orderId: String): OrderPaymentSummary {
        val order = orderRepository.findByOrderId(orderId)
            ?: throw NoSuchElementException("Order not found: $orderId")
        require(order.status != OrderStatus.PAID) { "Order is already paid: $orderId" }
        return LogContext.with(LogContext.ORDER_ID to order.orderId) {
            log.info(
                "order payable check passed status={} merchantId={} branchId={} totalAmountMinor={} currency={}",
                order.status, order.merchantApplicationId, order.merchantBranchId, order.totalAmountMinor, order.currency,
            )
            OrderPaymentSummary(
                orderId = order.orderId,
                merchantId = order.merchantApplicationId,
                branchId = order.merchantBranchId,
                totalAmountMinor = order.totalAmountMinor,
                currency = order.currency,
            )
        }
    }

    override fun linkPaymentAttempt(
        orderId: String,
        paymentRequestId: UUID,
        provider: String,
        providerReference: String?,
    ): OrderPaymentSummary {
        val order = orderRepository.findByOrderId(orderId)
            ?: throw NoSuchElementException("Order not found: $orderId")
        require(order.status != OrderStatus.PAID) { "Order is already paid: $orderId" }

        return LogContext.with(
            LogContext.ORDER_ID to order.orderId,
            LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
            LogContext.PROVIDER to provider,
        ) {
            val existingAttempt = paymentAttemptRepository.findByPaymentRequestId(paymentRequestId)
            val attempt = existingAttempt
                ?: PaymentAttempt(
                    orderIdFk = order.id,
                    orderId = order.orderId,
                    paymentRequestId = paymentRequestId,
                    provider = provider,
                    providerReference = providerReference,
                )
            log.info(
                "link payment attempt existing={} orderStatus={} providerReference={}",
                existingAttempt != null, order.status, providerReference,
            )
            attempt.provider = provider
            attempt.update(providerReference = providerReference, at = Instant.now())
            paymentAttemptRepository.save(attempt)

            order.markPaymentInProgress(paymentRequestId, provider)
            checkoutPaymentMethodForProvider(provider)?.let { order.checkoutPaymentMethod = it.name }
            orderRepository.save(order)
            log.info(
                "payment attempt linked orderStatus={} attemptStatus={} providerReference={}",
                order.status, attempt.status, attempt.providerReference,
            )
            assertPayable(orderId)
        }
    }

    override fun isShopperOrder(orderId: String, shopperId: String): Boolean {
        val order = orderRepository.findByOrderId(orderId) ?: return false
        return (order.creatorType == CreatorType.SHOPPER && order.createdById == shopperId)
            || order.targetShopperId == shopperId
    }

    override fun isMerchantOrder(orderId: String, merchantId: UUID, branchId: UUID?): Boolean {
        val order = orderRepository.findByOrderId(orderId) ?: return false
        if (order.merchantApplicationId != merchantId) return false
        return branchId == null || order.merchantBranchId == branchId
    }

    @Transactional(readOnly = true)
    override fun findLastPaymentRequestId(orderId: String): UUID? =
        orderRepository.findByOrderId(orderId)?.lastPaymentRequestId

    private fun checkoutPaymentMethodForProvider(provider: String): CheckoutPaymentMethod? =
        when (provider) {
            "TRUELAYER" -> CheckoutPaymentMethod.BANK_ACCOUNT
            "STRIPE" -> CheckoutPaymentMethod.CARD
            "STRIPE_PAY_BY_BANK" -> CheckoutPaymentMethod.PAY_BY_BANK
            "STRIPE_TERMINAL" -> CheckoutPaymentMethod.TERMINAL
            "BRAINTREE" -> CheckoutPaymentMethod.PAYPAL
            else -> null
        }
}
