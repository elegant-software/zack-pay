package com.elegant.software.blitzpay.order.application

import com.elegant.software.blitzpay.order.domain.CreatorType
import com.elegant.software.blitzpay.order.domain.Order
import com.elegant.software.blitzpay.order.domain.OrderStatus
import com.elegant.software.blitzpay.order.domain.PaymentAttempt
import com.elegant.software.blitzpay.order.repository.OrderRepository
import com.elegant.software.blitzpay.order.repository.PaymentAttemptRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import kotlin.test.assertEquals

class PaymentAttemptServiceTest {
    private val orderRepository = mock<OrderRepository>()
    private val paymentAttemptRepository = mock<PaymentAttemptRepository>()
    private val service = PaymentAttemptService(orderRepository, paymentAttemptRepository)

    @Test
    fun `linkPaymentAttempt captures pay by bank checkout method from provider`() {
        val order = payableOrder().apply { checkoutPaymentMethod = "CARD" }
        val paymentRequestId = UUID.randomUUID()
        whenever(orderRepository.findByOrderId(order.orderId)).thenReturn(order)
        whenever(paymentAttemptRepository.findByPaymentRequestId(paymentRequestId)).thenReturn(null)
        whenever(paymentAttemptRepository.save(any<PaymentAttempt>())).thenAnswer { it.arguments[0] }
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }

        service.linkPaymentAttempt(order.orderId, paymentRequestId, "STRIPE_PAY_BY_BANK", null)

        assertEquals("PAY_BY_BANK", order.checkoutPaymentMethod)
        assertEquals(OrderStatus.PAYMENT_INITIATED, order.status)
    }

    @Test
    fun `linkPaymentAttempt captures card checkout method from generic Stripe provider`() {
        val order = payableOrder()
        val paymentRequestId = UUID.randomUUID()
        whenever(orderRepository.findByOrderId(order.orderId)).thenReturn(order)
        whenever(paymentAttemptRepository.findByPaymentRequestId(paymentRequestId)).thenReturn(null)
        whenever(paymentAttemptRepository.save(any<PaymentAttempt>())).thenAnswer { it.arguments[0] }
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }

        service.linkPaymentAttempt(order.orderId, paymentRequestId, "STRIPE", null)

        assertEquals("CARD", order.checkoutPaymentMethod)
    }

    private fun payableOrder(): Order =
        Order(
            orderId = "ORDER-123",
            merchantApplicationId = UUID.randomUUID(),
            creatorType = CreatorType.SHOPPER,
            createdById = "shopper-test",
            currency = "EUR",
            totalAmountMinor = 1099,
            itemCount = 1,
            status = OrderStatus.CREATED,
        )
}
