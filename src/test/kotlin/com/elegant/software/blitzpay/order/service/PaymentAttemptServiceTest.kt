package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.order.persistence.model.CreatorType
import com.elegant.software.blitzpay.order.persistence.model.Order
import com.elegant.software.blitzpay.order.persistence.model.OrderStatus
import com.elegant.software.blitzpay.order.persistence.model.PaymentAttempt
import com.elegant.software.blitzpay.order.persistence.repository.OrderItemRepository
import com.elegant.software.blitzpay.order.persistence.repository.OrderRepository
import com.elegant.software.blitzpay.order.persistence.repository.PaymentAttemptRepository
import com.elegant.software.blitzpay.shopper.api.ShopperProfileData
import com.elegant.software.blitzpay.shopper.api.ShopperProfileGateway
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentAttemptServiceTest {
    private val orderRepository = mock<OrderRepository>()
    private val paymentAttemptRepository = mock<PaymentAttemptRepository>()
    private val orderItemRepository = mock<OrderItemRepository>()
    private val shopperProfileGateway = mock<ShopperProfileGateway>()
    private val service = PaymentAttemptService(orderRepository, paymentAttemptRepository, orderItemRepository, shopperProfileGateway)

    @Test
    fun `findInvoiceSource for paid shopper order falls back to shopper profile contact and email`() {
        val paidAt = Instant.parse("2026-10-01T10:00:00Z")
        val order = payableOrder().apply { status = OrderStatus.PAID; this.paidAt = paidAt }
        whenever(orderRepository.findByOrderId(order.orderId)).thenReturn(order)
        whenever(orderItemRepository.findAllByOrderIdFk(order.id)).thenReturn(emptyList())
        whenever(shopperProfileGateway.findByShopperId("shopper-test"))
            .thenReturn(ShopperProfileData("Alice", "1 Lane", "10115", "Berlin", "DE", email = "alice@example.com"))

        val source = service.findInvoiceSource(order.orderId)!!

        assertTrue(source.paid)
        assertEquals(paidAt, source.issuedAt)
        assertEquals("Alice", source.buyerContactName)
        assertEquals("DE", source.buyerContactCountry)
        assertEquals("alice@example.com", source.buyerEmail)
        assertEquals(false, source.isInvoiceOrder)
    }

    @Test
    fun `findInvoiceSource for invoice order uses createdAt and target shopper email`() {
        val createdAt = Instant.parse("2026-10-02T09:00:00Z")
        val order = Order(
            orderId = "ORDER-INV",
            merchantApplicationId = UUID.randomUUID(),
            creatorType = CreatorType.MERCHANT,
            createdById = "merchant-user",
            currency = "EUR",
            totalAmountMinor = 5000,
            itemCount = 1,
            createdAt = createdAt,
            targetShopperId = "shopper-target",
            dueDate = LocalDate.parse("2026-10-16"),
        )
        whenever(orderRepository.findByOrderId(order.orderId)).thenReturn(order)
        whenever(orderItemRepository.findAllByOrderIdFk(order.id)).thenReturn(emptyList())
        whenever(shopperProfileGateway.findByShopperId("shopper-target"))
            .thenReturn(ShopperProfileData("Bob", "", "", "", "", email = "bob@example.com"))

        val source = service.findInvoiceSource(order.orderId)!!

        assertEquals(false, source.paid)
        assertTrue(source.isInvoiceOrder)
        assertEquals(createdAt, source.issuedAt)
        assertEquals(LocalDate.parse("2026-10-16"), source.dueDate)
        assertEquals("bob@example.com", source.buyerEmail)
        assertNull(source.buyerContactName)
    }

    @Test
    fun `findInvoiceSource for paid guest order returns the consented invoice email`() {
        val order = guestOrder("guest@example.com")
        whenever(orderRepository.findByOrderId(order.orderId)).thenReturn(order)
        whenever(orderItemRepository.findAllByOrderIdFk(order.id)).thenReturn(emptyList())

        val source = service.findInvoiceSource(order.orderId)!!

        assertEquals("guest@example.com", source.buyerEmail)
        assertTrue(source.isGuestOrder)
        assertNull(source.buyerContactName)
    }

    @Test
    fun `clearGuestInvoiceEmail drops the email but keeps the consent record`() {
        val order = guestOrder("guest@example.com")
        whenever(orderRepository.findByOrderId(order.orderId)).thenReturn(order)

        service.clearGuestInvoiceEmail(order.orderId)

        assertNull(order.invoiceEmail)
        assertNotNull(order.invoiceEmailConsentAt)
        verify(orderRepository).save(order)
    }

    private fun guestOrder(invoiceEmail: String) = Order(
        orderId = "ORDER-GUEST",
        merchantApplicationId = UUID.randomUUID(),
        creatorType = CreatorType.GUEST,
        createdById = "guest",
        currency = "EUR",
        totalAmountMinor = 1099,
        itemCount = 1,
        status = OrderStatus.PAID,
        paidAt = Instant.parse("2026-11-08T10:00:00Z"),
        invoiceEmail = invoiceEmail,
        invoiceEmailConsentAt = Instant.parse("2026-11-08T09:55:00Z"),
        invoiceEmailConsentVersion = "v1",
    )

    @Test
    fun `findInvoiceSource returns null for unpaid regular order`() {
        val order = payableOrder()
        whenever(orderRepository.findByOrderId(order.orderId)).thenReturn(order)

        assertNull(service.findInvoiceSource(order.orderId))
    }

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
