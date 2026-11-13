package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.api.InvoiceService
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoiceRepository
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceGateway
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceProfile
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderInvoiceSource
import com.elegant.software.blitzpay.storage.StorageService
import com.elegant.software.blitzpay.support.TestFixtureLoader
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import kotlin.test.*

class OrderInvoiceProviderTest {
    private val orders = mock<OrderGateway>()
    private val merchants = mock<MerchantInvoiceGateway>()
    private val renderer = mock<InvoiceService>()
    private val storage = mock<StorageService>()
    private val invoices = mock<OrderInvoiceRepository>()
    private val provider = OrderInvoiceProvider(orders, merchants, renderer, storage, invoices)
    private val source = TestFixtureLoader.load("testdata/invoice/paid-download.json", OrderInvoiceSource::class.java)

    @Test
    fun `paid invoice remains downloadable without SMTP or an email address`() {
        whenever(orders.findInvoiceSource(source.orderId)).thenReturn(source)
        val invoice = OrderInvoice(orderIdFk = source.orderIdFk, orderId = source.orderId,
            s3Key = "invoices/${source.orderId}.pdf", generatedAt = Instant.parse("2026-11-11T12:01:00Z"))
        whenever(invoices.findByOrderId(source.orderId)).thenReturn(invoice)

        assertEquals(invoice, provider.findOrCreate(source.orderId))
        verifyNoInteractions(renderer, storage)
    }

    @Test
    fun `unpaid order cannot download even a previously generated invoice`() {
        whenever(orders.findInvoiceSource(source.orderId)).thenReturn(source.copy(paid = false, isInvoiceOrder = true))
        assertNull(provider.findOrCreate(source.orderId))
        verifyNoInteractions(invoices, renderer, storage)
    }

    @Test
    fun `first download generates a paid receipt without buyer contact or email`() {
        whenever(orders.findInvoiceSource(source.orderId)).thenReturn(source)
        whenever(invoices.tryAdvisoryXactLock(InvoiceBatchJob.LOCK_KEY)).thenReturn(true)
        whenever(merchants.findForInvoice(source.merchantId)).thenReturn(MerchantInvoiceProfile(
            merchantName = "Test Bakery", registrationNumber = "REG-TEST", addressLine1 = "Baker Street 1",
            city = "Berlin", postalCode = "10115", country = "DE", defaultVatPercent = null, logoStorageKey = null))
        whenever(renderer.generatePdf(any())).thenReturn("PDF".toByteArray())

        assertEquals("invoices/${source.orderId}.pdf", provider.findOrCreate(source.orderId)?.s3Key)
        verify(renderer).generatePdf(argThat { buyer.name == "—" && lineItems.single().description == "Coffee" })
        verify(storage).upload(eq("invoices/${source.orderId}.pdf"), eq("application/pdf"), any())
    }

    @Test
    fun `concurrent rendering is deferred while another replica owns the lock`() {
        whenever(orders.findInvoiceSource(source.orderId)).thenReturn(source)
        assertNull(provider.findOrCreate(source.orderId))
        verifyNoInteractions(renderer, storage)
    }
}
