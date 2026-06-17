package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.api.InvoiceService
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoiceRepository
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceGateway
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceProfile
import com.elegant.software.blitzpay.order.api.OrderItemSnapshot
import com.elegant.software.blitzpay.order.api.OrderPaid
import com.elegant.software.blitzpay.storage.StorageService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class InvoiceOrchestrationListenerTest {

    private val merchantInvoiceGateway = mock<MerchantInvoiceGateway>()
    private val invoiceService = mock<InvoiceService>()
    private val storageService = mock<StorageService>()
    private val orderInvoiceRepository = mock<OrderInvoiceRepository>()

    private val listener = InvoiceOrchestrationListener(
        merchantInvoiceGateway, invoiceService, storageService, orderInvoiceRepository,
    )

    private val merchantId = UUID.randomUUID()
    private val orderIdFk = UUID.randomUUID()
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

    private fun paidEvent(
        buyerContactName: String? = "Alice Smith",
        buyerContactStreet: String? = "1 Shopper Lane",
        buyerContactZip: String? = "EC1A 1BB",
        buyerContactCity: String? = "London",
        buyerContactCountry: String? = "GB",
        items: List<OrderItemSnapshot> = listOf(OrderItemSnapshot("Croissant", 2, 350)),
    ) = OrderPaid(
        orderId = "ORD-TESTORDER001",
        orderIdFk = orderIdFk,
        merchantId = merchantId,
        currency = "EUR",
        totalAmountMinor = 700,
        buyerContactName = buyerContactName,
        buyerContactStreet = buyerContactStreet,
        buyerContactZip = buyerContactZip,
        buyerContactCity = buyerContactCity,
        buyerContactCountry = buyerContactCountry,
        paidAt = Instant.now(),
        items = items,
    )

    @Test
    fun `generates and stores invoice when buyer contact is present`() {
        val event = paidEvent()
        whenever(orderInvoiceRepository.existsByOrderId(event.orderId)).thenReturn(false)
        whenever(merchantInvoiceGateway.findForInvoice(merchantId)).thenReturn(merchant)
        whenever(invoiceService.generatePdf(any())).thenReturn(pdfBytes)

        listener.on(event)

        verify(storageService).upload(eq("invoices/${event.orderId}.pdf"), eq("application/pdf"), eq(pdfBytes))
        verify(orderInvoiceRepository).save(any<OrderInvoice>())
    }

    @Test
    fun `skips silently when buyer contact is absent`() {
        val event = paidEvent(buyerContactName = null)

        listener.on(event)

        verify(invoiceService, never()).generatePdf(any())
        verify(storageService, never()).upload(any(), any(), any())
        verify(orderInvoiceRepository, never()).save(any())
    }

    @Test
    fun `skips when any buyer contact field is missing`() {
        val event = paidEvent(buyerContactStreet = null)

        listener.on(event)

        verify(invoiceService, never()).generatePdf(any())
    }

    @Test
    fun `skips when merchant is not found`() {
        val event = paidEvent()
        whenever(orderInvoiceRepository.existsByOrderId(event.orderId)).thenReturn(false)
        whenever(merchantInvoiceGateway.findForInvoice(merchantId)).thenReturn(null)

        listener.on(event)

        verify(invoiceService, never()).generatePdf(any())
        verify(storageService, never()).upload(any(), any(), any())
    }

    @Test
    fun `skips when invoice already exists for order`() {
        val event = paidEvent()
        whenever(orderInvoiceRepository.existsByOrderId(event.orderId)).thenReturn(true)

        listener.on(event)

        verify(merchantInvoiceGateway, never()).findForInvoice(any())
        verify(invoiceService, never()).generatePdf(any())
    }

    @Test
    fun `builds one line item per order item`() {
        val items = listOf(
            OrderItemSnapshot("Croissant", 2, 350),
            OrderItemSnapshot("Coffee", 1, 250),
        )
        val event = paidEvent(items = items)
        whenever(orderInvoiceRepository.existsByOrderId(event.orderId)).thenReturn(false)
        whenever(merchantInvoiceGateway.findForInvoice(merchantId)).thenReturn(merchant)
        whenever(invoiceService.generatePdf(any())).thenReturn(pdfBytes)

        listener.on(event)

        verify(invoiceService).generatePdf(
            org.mockito.kotlin.argThat { invoiceData ->
                invoiceData.lineItems.size == 2 &&
                    invoiceData.lineItems[0].description == "Croissant" &&
                    invoiceData.lineItems[1].description == "Coffee"
            }
        )
    }

    @Test
    fun `uses merchant default VAT rate on line items`() {
        val event = paidEvent()
        whenever(orderInvoiceRepository.existsByOrderId(event.orderId)).thenReturn(false)
        whenever(merchantInvoiceGateway.findForInvoice(merchantId)).thenReturn(merchant)
        whenever(invoiceService.generatePdf(any())).thenReturn(pdfBytes)

        listener.on(event)

        verify(invoiceService).generatePdf(
            org.mockito.kotlin.argThat { invoiceData ->
                invoiceData.lineItems.all { it.vatPercent == BigDecimal("20.00") }
            }
        )
    }

    @Test
    fun `uses zero VAT when merchant has no default rate`() {
        val event = paidEvent()
        val merchantNoVat = merchant.copy(defaultVatPercent = null)
        whenever(orderInvoiceRepository.existsByOrderId(event.orderId)).thenReturn(false)
        whenever(merchantInvoiceGateway.findForInvoice(merchantId)).thenReturn(merchantNoVat)
        whenever(invoiceService.generatePdf(any())).thenReturn(pdfBytes)

        listener.on(event)

        verify(invoiceService).generatePdf(
            org.mockito.kotlin.argThat { invoiceData ->
                invoiceData.lineItems.all { it.vatPercent == BigDecimal.ZERO }
            }
        )
    }

    @Test
    fun `includes merchant logo as base64 when logo key present`() {
        val logoBytes = "PNG".toByteArray()
        val merchantWithLogo = merchant.copy(logoStorageKey = "logos/merchant-123.png")
        val event = paidEvent()
        whenever(orderInvoiceRepository.existsByOrderId(event.orderId)).thenReturn(false)
        whenever(merchantInvoiceGateway.findForInvoice(merchantId)).thenReturn(merchantWithLogo)
        whenever(storageService.download("logos/merchant-123.png")).thenReturn(logoBytes)
        whenever(invoiceService.generatePdf(any())).thenReturn(pdfBytes)

        listener.on(event)

        verify(invoiceService).generatePdf(
            org.mockito.kotlin.argThat { invoiceData -> invoiceData.logoBase64 != null }
        )
    }

    @Test
    fun `proceeds without logo when logo fetch fails`() {
        val merchantWithLogo = merchant.copy(logoStorageKey = "logos/missing.png")
        val event = paidEvent()
        whenever(orderInvoiceRepository.existsByOrderId(event.orderId)).thenReturn(false)
        whenever(merchantInvoiceGateway.findForInvoice(merchantId)).thenReturn(merchantWithLogo)
        whenever(storageService.download(any())).thenThrow(RuntimeException("S3 error"))
        whenever(invoiceService.generatePdf(any())).thenReturn(pdfBytes)

        listener.on(event)

        verify(invoiceService).generatePdf(
            org.mockito.kotlin.argThat { invoiceData -> invoiceData.logoBase64 == null }
        )
        verify(orderInvoiceRepository).save(any())
    }
}
