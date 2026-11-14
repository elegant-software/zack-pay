package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.api.InvoiceService
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice.DeliveryStatus
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoiceRepository
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceGateway
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceProfile
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderInvoiceSource
import com.elegant.software.blitzpay.order.api.OrderItemSnapshot
import com.elegant.software.blitzpay.storage.StorageService
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSender
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.Properties
import java.util.UUID
import kotlin.test.assertEquals

class InvoiceBatchJobTest {

    private val orderGateway = mock<OrderGateway>()
    private val merchantInvoiceGateway = mock<MerchantInvoiceGateway>()
    private val invoiceService = mock<InvoiceService>()
    private val storageService = mock<StorageService>()
    private val orderInvoiceRepository = mock<OrderInvoiceRepository>()
    private val mailSender = mock<JavaMailSender>()

    private val job = InvoiceBatchJob(
        orderGateway, storageService, orderInvoiceRepository, mailSender,
        OrderInvoiceProvider(orderGateway, merchantInvoiceGateway, invoiceService, storageService, orderInvoiceRepository),
        fromAddress = "invoices@blitzpay.test", lookbackDays = 7, batchSize = 20,
    )

    private val orderId = "ORD-TESTORDER001"
    private val merchantId = UUID.randomUUID()
    private val pdfBytes = "PDF".toByteArray()

    private val merchant = MerchantInvoiceProfile(
        merchantName = "Test Bakery",
        registrationNumber = "REG-001",
        addressLine1 = "123 Baker Street",
        city = "London",
        postalCode = "W1A 1AA",
        country = "GB",
        defaultVatPercent = BigDecimal("20.00"),
        logoStorageKey = null,
    )

    private fun paidSource(
        buyerContactName: String? = "Alice Smith",
        buyerContactStreet: String? = "1 Shopper Lane",
        items: List<OrderItemSnapshot> = listOf(OrderItemSnapshot("Croissant", 2, 350)),
        buyerEmail: String? = "alice@example.com",
    ) = OrderInvoiceSource(
        orderId = orderId,
        orderIdFk = UUID.randomUUID(),
        merchantId = merchantId,
        currency = "EUR",
        totalAmountMinor = 700,
        buyerContactName = buyerContactName,
        buyerContactStreet = buyerContactStreet,
        buyerContactZip = "EC1A 1BB",
        buyerContactCity = "London",
        buyerContactCountry = "GB",
        issuedAt = Instant.parse("2026-10-30T23:30:00Z"),
        dueDate = null,
        isInvoiceOrder = false,
        items = items,
        buyerEmail = buyerEmail,
        paid = true,
    )

    private fun invoiceOrderSource() = paidSource(buyerContactName = null, buyerContactStreet = null, items = emptyList())
        .copy(
            buyerContactZip = null, buyerContactCity = null, buyerContactCountry = null,
            isInvoiceOrder = true, paid = false, dueDate = LocalDate.parse("2026-11-14"),
        )

    private fun guestSource(buyerEmail: String?) = paidSource(buyerContactName = null, buyerContactStreet = null, buyerEmail = buyerEmail)
        .copy(buyerContactZip = null, buyerContactCity = null, buyerContactCountry = null, isGuestOrder = true)

    @BeforeEach
    fun setUp() {
        whenever(orderInvoiceRepository.tryAdvisoryXactLock(InvoiceBatchJob.LOCK_KEY)).thenReturn(true)
        whenever(orderInvoiceRepository.findOrderIdsGeneratedAfter(any())).thenReturn(emptyList())
        whenever(orderInvoiceRepository.findByDeliveryStatusOrderByGeneratedAt(eq(DeliveryStatus.PENDING), any()))
            .thenReturn(emptyList())
        whenever(merchantInvoiceGateway.findForInvoice(merchantId)).thenReturn(merchant)
        whenever(invoiceService.generatePdf(any())).thenReturn(pdfBytes)
        whenever(mailSender.createMimeMessage()).thenAnswer { MimeMessage(Session.getInstance(Properties())) }
    }

    private fun givenMissingInvoiceFor(source: OrderInvoiceSource) {
        whenever(orderGateway.findInvoiceableOrderIds(any())).thenReturn(listOf(source.orderId))
        whenever(orderGateway.findInvoiceSource(source.orderId)).thenReturn(source)
    }

    private fun givenPendingInvoice(): OrderInvoice {
        val invoice = OrderInvoice(
            orderIdFk = UUID.randomUUID(), orderId = orderId, s3Key = "invoices/$orderId.pdf", generatedAt = Instant.now(),
        )
        whenever(orderInvoiceRepository.findByDeliveryStatusOrderByGeneratedAt(eq(DeliveryStatus.PENDING), any()))
            .thenReturn(listOf(invoice))
        whenever(storageService.download(invoice.s3Key)).thenReturn(pdfBytes)
        return invoice
    }

    @Test
    fun `does nothing when another instance holds the lock`() {
        whenever(orderInvoiceRepository.tryAdvisoryXactLock(InvoiceBatchJob.LOCK_KEY)).thenReturn(false)

        job.run()

        verifyNoInteractions(orderGateway, invoiceService, storageService, mailSender)
        verify(orderInvoiceRepository, never()).save(any<OrderInvoice>())
    }

    @Test
    fun `generates and stores invoice for paid order with buyer contact`() {
        givenMissingInvoiceFor(paidSource())

        job.run()

        verify(storageService).upload(eq("invoices/$orderId.pdf"), eq("application/pdf"), eq(pdfBytes))
        verify(orderInvoiceRepository).save(argThat<OrderInvoice> {
            this.orderId == this@InvoiceBatchJobTest.orderId && deliveryStatus == DeliveryStatus.PENDING
        })
        verify(invoiceService).generatePdf(argThat {
            issueDate == LocalDate.parse("2026-10-30") && dueDate == issueDate &&
                buyer.name == "Alice Smith" &&
                lineItems.single().description == "Croissant" &&
                lineItems.single().vatPercent == BigDecimal("20.00") &&
                // 3.50 gross at 20% VAT -> 2.9167 net, so the invoice total equals the order total
                lineItems.single().unitPrice == BigDecimal("2.9167")
        })
    }

    @Test
    fun `skips orders that already have an invoice`() {
        givenMissingInvoiceFor(paidSource())
        whenever(orderInvoiceRepository.findOrderIdsGeneratedAfter(any())).thenReturn(listOf(orderId))

        job.run()

        verify(orderGateway, never()).findInvoiceSource(any())
        verify(invoiceService, never()).generatePdf(any())
    }

    @Test
    fun `generates a paid receipt with incomplete buyer contact`() {
        givenMissingInvoiceFor(paidSource(buyerContactStreet = null))

        job.run()

        verify(invoiceService).generatePdf(any())
        verify(orderInvoiceRepository).save(any<OrderInvoice>())
    }

    @Test
    fun `generates invoice order without buyer contact using placeholder and due date`() {
        givenMissingInvoiceFor(invoiceOrderSource())

        job.run()

        verify(invoiceService).generatePdf(argThat {
            buyer.name == "—" && dueDate == LocalDate.parse("2026-11-14") &&
                lineItems.single().description == "Order $orderId" &&
                lineItems.single().unitPrice == BigDecimal("5.8333")
        })
        verify(orderInvoiceRepository).save(any<OrderInvoice>())
    }

    @Test
    fun `generates guest invoice with placeholder buyer when the guest consented to email`() {
        givenMissingInvoiceFor(guestSource(buyerEmail = "guest@example.com"))

        job.run()

        verify(invoiceService).generatePdf(argThat { buyer.name == "—" && lineItems.single().description == "Croissant" })
        verify(orderInvoiceRepository).save(any<OrderInvoice>())
    }

    @Test
    fun `leaves guest receipts without email to on-demand downloads`() {
        givenMissingInvoiceFor(guestSource(buyerEmail = null))

        job.run()

        verify(invoiceService, never()).generatePdf(any())
    }

    @Test
    fun `clears the guest email after the invoice is sent`() {
        givenPendingInvoice()
        whenever(orderGateway.findInvoiceSource(orderId)).thenReturn(guestSource(buyerEmail = "guest@example.com"))

        job.run()

        verify(mailSender).send(any<MimeMessage>())
        verify(orderGateway).clearGuestInvoiceEmail(orderId)
    }

    @Test
    fun `keeps the guest email while sending fails so the retry can use it`() {
        givenPendingInvoice()
        whenever(orderGateway.findInvoiceSource(orderId)).thenReturn(guestSource(buyerEmail = "guest@example.com"))
        doThrow(MailSendException("SMTP down")).whenever(mailSender).send(any<MimeMessage>())

        job.run()

        verify(orderGateway, never()).clearGuestInvoiceEmail(any())
    }

    @Test
    fun `skips when merchant is not found`() {
        givenMissingInvoiceFor(paidSource())
        whenever(merchantInvoiceGateway.findForInvoice(merchantId)).thenReturn(null)

        job.run()

        verify(invoiceService, never()).generatePdf(any())
        verify(storageService, never()).upload(any(), any(), any())
    }

    @Test
    fun `uses zero VAT when merchant has no default rate`() {
        givenMissingInvoiceFor(paidSource())
        whenever(merchantInvoiceGateway.findForInvoice(merchantId)).thenReturn(merchant.copy(defaultVatPercent = null))

        job.run()

        verify(invoiceService).generatePdf(argThat { lineItems.all { it.vatPercent == BigDecimal.ZERO } })
    }

    @Test
    fun `proceeds without logo when logo fetch fails`() {
        givenMissingInvoiceFor(paidSource())
        whenever(merchantInvoiceGateway.findForInvoice(merchantId))
            .thenReturn(merchant.copy(logoStorageKey = "logos/missing.png"))
        whenever(storageService.download("logos/missing.png")).thenThrow(RuntimeException("S3 error"))

        job.run()

        verify(invoiceService).generatePdf(argThat { logoBase64 == null })
        verify(orderInvoiceRepository).save(any<OrderInvoice>())
    }

    @Test
    fun `emails pending invoice and marks it SENT`() {
        val invoice = givenPendingInvoice()
        whenever(orderGateway.findInvoiceSource(orderId)).thenReturn(paidSource())

        job.run()

        verify(mailSender).send(any<MimeMessage>())
        assertEquals(DeliveryStatus.SENT, invoice.deliveryStatus)
    }

    @Test
    fun `marks pending invoice SKIPPED when shopper has no email`() {
        val invoice = givenPendingInvoice()
        whenever(orderGateway.findInvoiceSource(orderId)).thenReturn(paidSource(buyerEmail = null))

        job.run()

        verify(mailSender, never()).send(any<MimeMessage>())
        assertEquals(DeliveryStatus.SKIPPED, invoice.deliveryStatus)
    }

    @Test
    fun `keeps invoice PENDING when sending fails`() {
        val invoice = givenPendingInvoice()
        whenever(orderGateway.findInvoiceSource(orderId)).thenReturn(paidSource())
        doThrow(MailSendException("SMTP down")).whenever(mailSender).send(any<MimeMessage>())

        job.run()

        assertEquals(DeliveryStatus.PENDING, invoice.deliveryStatus)
    }

    @Test
    fun `does not send an existing invoice before payment is confirmed`() {
        val invoice = givenPendingInvoice()
        whenever(orderGateway.findInvoiceSource(orderId)).thenReturn(invoiceOrderSource().copy(paid = false))

        job.run()

        verify(mailSender, never()).send(any<MimeMessage>())
        assertEquals(DeliveryStatus.PENDING, invoice.deliveryStatus)
    }

    @Test
    fun `unpaid invoices at the front of the queue do not block paid invoice emails`() {
        val paid = givenPendingInvoice()
        val unpaid = OrderInvoice(orderIdFk = UUID.randomUUID(), orderId = "ORD-UNPAID001",
            s3Key = "invoices/ORD-UNPAID001.pdf", generatedAt = Instant.parse("2026-11-10T10:00:00Z"))
        whenever(orderInvoiceRepository.findByDeliveryStatusOrderByGeneratedAt(
            DeliveryStatus.PENDING, org.springframework.data.domain.PageRequest.of(0, 1, org.springframework.data.domain.Sort.by("id")))).thenReturn(listOf(unpaid))
        whenever(orderInvoiceRepository.findByDeliveryStatusOrderByGeneratedAt(
            DeliveryStatus.PENDING, org.springframework.data.domain.PageRequest.of(1, 1, org.springframework.data.domain.Sort.by("id")))).thenReturn(listOf(paid))
        whenever(orderGateway.findInvoiceSource(unpaid.orderId)).thenReturn(invoiceOrderSource().copy(paid = false))
        whenever(orderGateway.findInvoiceSource(orderId)).thenReturn(paidSource())
        val singleInvoiceBatch = InvoiceBatchJob(orderGateway, storageService, orderInvoiceRepository, mailSender,
            OrderInvoiceProvider(orderGateway, merchantInvoiceGateway, invoiceService, storageService, orderInvoiceRepository),
            "invoices@blitzpay.test", 7, 1)

        singleInvoiceBatch.run()

        verify(mailSender).send(any<MimeMessage>())
        assertEquals(DeliveryStatus.SENT, paid.deliveryStatus)
        assertEquals(DeliveryStatus.PENDING, unpaid.deliveryStatus)
    }
}
