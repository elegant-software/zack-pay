package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.order.OrderFixtureLoader
import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.PaymentAttempt
import com.elegant.software.blitzpay.order.persistence.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.persistence.repository.OrderRepository
import com.elegant.software.blitzpay.order.persistence.repository.PaymentAttemptRepository
import com.elegant.software.blitzpay.order.api.PaymentStatusCode
import com.elegant.software.blitzpay.shopper.api.ShopperProfileGateway
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
    private val storefrontOrderBus = mock<StorefrontOrderBus>()
    private val projection = OrderStatusProjection(orderRepository, paymentAttemptRepository, orderItemRepository, eventPublisher, shopperProfileGateway, storefrontOrderBus, org.mockito.kotlin.mock<com.elegant.software.blitzpay.merchant.api.TableGateway>())

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
        verify(storefrontOrderBus, never()).publishAfterCommit(any(), any())
    }

    @Test
    fun `payment status change of a guest order is pushed to its storefront stream`() {
        val event = OrderFixtureLoader.settledEvent()
        val order = Order(
            orderId = "ORDER-GUEST",
            merchantApplicationId = java.util.UUID.randomUUID(),
            creatorType = CreatorType.GUEST,
            createdById = "guest",
            currency = "EUR",
            totalAmountMinor = 1099,
            itemCount = 1,
            status = OrderStatus.PAYMENT_INITIATED,
        )
        val attempt = PaymentAttempt(orderIdFk = order.id, orderId = order.orderId, paymentRequestId = event.paymentRequestId, provider = "STRIPE")
        whenever(paymentAttemptRepository.findByPaymentRequestId(event.paymentRequestId)).thenReturn(attempt)
        whenever(orderRepository.findById(order.id)).thenReturn(Optional.of(order))
        whenever(orderRepository.save(any<Order>())).thenAnswer { it.arguments[0] }
        whenever(paymentAttemptRepository.save(any<PaymentAttempt>())).thenAnswer { it.arguments[0] }
        whenever(orderItemRepository.findAllByOrderIdFk(order.id)).thenReturn(emptyList())

        projection.on(event)

        verify(storefrontOrderBus).publishAfterCommit(eq(order), any())
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
