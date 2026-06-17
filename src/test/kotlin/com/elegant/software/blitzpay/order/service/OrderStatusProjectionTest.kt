package com.elegant.software.blitzpay.order.application

import com.elegant.software.blitzpay.order.OrderFixtureLoader
import com.elegant.software.blitzpay.order.domain.CreatorType
import com.elegant.software.blitzpay.order.domain.Order
import com.elegant.software.blitzpay.order.domain.OrderStatus
import com.elegant.software.blitzpay.order.domain.PaymentAttempt
import com.elegant.software.blitzpay.order.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.repository.OrderRepository
import com.elegant.software.blitzpay.order.repository.PaymentAttemptRepository
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusCode
import com.elegant.software.blitzpay.shopper.api.ShopperProfileGateway
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals

class OrderStatusProjectionTest {
    private val orderRepository = mock<OrderRepository>()
    private val paymentAttemptRepository = mock<PaymentAttemptRepository>()
    private val orderItemRepository = mock<OrderItemRepository>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val shopperProfileGateway = mock<ShopperProfileGateway>()
    private val projection = OrderStatusProjection(orderRepository, paymentAttemptRepository, orderItemRepository, eventPublisher, shopperProfileGateway)

    @Test
    fun `settled event marks order as paid`() {
        val event = OrderFixtureLoader.settledEvent()
        val order = Order(
            orderId = "ORDER-123",
            merchantApplicationId = java.util.UUID.randomUUID(),
            creatorType = CreatorType.SHOPPER,
            createdById = "shopper-test",
            currency = "EUR",
            totalAmountMinor = 1099,
            itemCount = 1,
            status = OrderStatus.PAYMENT_INITIATED,
        )
        val attempt = PaymentAttempt(
            orderIdFk = order.id,
            orderId = order.orderId,
            paymentRequestId = event.paymentRequestId,
            provider = "TRUELAYER"
        )
        whenever(paymentAttemptRepository.findByPaymentRequestId(event.paymentRequestId)).thenReturn(attempt)
        whenever(orderRepository.findById(order.id)).thenReturn(Optional.of(order))
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(paymentAttemptRepository.save(any<PaymentAttempt>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.findAllByOrderIdFk(order.id)).thenReturn(emptyList())

        projection.on(event)

        assertEquals(OrderStatus.PAID, order.status)
        assertEquals(Instant.parse("2026-04-30T10:15:30Z"), order.paidAt)
        assertEquals("SETTLED", attempt.status)
    }

    @Test
    fun `stale failure does not regress paid order`() {
        val event = OrderFixtureLoader.settledEvent().copy(newStatus = PaymentStatusCode.FAILED)
        val order = Order(
            orderId = "ORDER-123",
            merchantApplicationId = java.util.UUID.randomUUID(),
            creatorType = CreatorType.SHOPPER,
            createdById = "shopper-test",
            currency = "EUR",
            totalAmountMinor = 1099,
            itemCount = 1,
            status = OrderStatus.PAID,
        ).apply { paidAt = Instant.parse("2026-04-30T10:15:30Z") }
        val attempt = PaymentAttempt(
            orderIdFk = order.id,
            orderId = order.orderId,
            paymentRequestId = event.paymentRequestId,
            provider = "TRUELAYER"
        )
        whenever(paymentAttemptRepository.findByPaymentRequestId(event.paymentRequestId)).thenReturn(attempt)
        whenever(orderRepository.findById(order.id)).thenReturn(Optional.of(order))
        whenever(paymentAttemptRepository.save(any<PaymentAttempt>())).thenAnswer { it.arguments[0] }

        projection.on(event)

        assertEquals(OrderStatus.PAID, order.status)
    }
}
