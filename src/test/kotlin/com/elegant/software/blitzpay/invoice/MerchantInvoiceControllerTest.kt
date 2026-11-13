package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoiceRepository
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.merchant.api.MerchantAccessGateway
import com.elegant.software.blitzpay.storage.StorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.test.assertNotNull

class MerchantInvoiceControllerTest {

    private val invoiceProvider = mock<OrderInvoiceProvider>()
    private val orderGateway = mock<OrderGateway>()
    private val storageService = mock<StorageService>()
    private val access = mock<MerchantAccessGateway>()
    private val controller = MerchantInvoiceController(invoiceProvider, orderGateway, storageService, access)

    private val merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val branchId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val orderId = "ORD-PAID0000001"

    @Test
    fun `returns presigned invoice url for merchant-owned order`() {
        whenever(access.worksAtBranch(merchantId, branchId, null)).thenReturn(true)
        val invoice = OrderInvoice(
            orderIdFk = UUID.randomUUID(),
            orderId = orderId,
            s3Key = "invoices/$orderId.pdf",
            generatedAt = Instant.parse("2026-06-17T10:15:30Z"),
        )
        whenever(orderGateway.isMerchantOrder(orderId, merchantId, branchId)).thenReturn(true)
        whenever(invoiceProvider.findOrCreate(orderId)).thenReturn(invoice)
        whenever(storageService.presignDownload(invoice.s3Key, ttlMinutes = 30)).thenReturn("https://cdn.example/invoice.pdf")

        val response = controller.getInvoice(orderId, merchantId, branchId)

        assertEquals(200, response.statusCode.value())
        val body = assertNotNull(response.body)
        assertEquals("https://cdn.example/invoice.pdf", body.url)
        assertEquals("invoice-$orderId.pdf", body.filename)
    }

    @Test
    fun `returns forbidden when order is outside merchant scope`() {
        whenever(orderGateway.isMerchantOrder(orderId, merchantId, branchId)).thenReturn(false)

        val response = controller.getInvoice(orderId, merchantId, branchId)

        assertEquals(403, response.statusCode.value())
        verifyNoInteractions(invoiceProvider)
        verify(storageService, never()).presignDownload(any(), any())
    }

    @Test
    fun `returns not found when owned order has no invoice`() {
        whenever(access.isManagerOrPlatformAdmin(merchantId, null)).thenReturn(true)
        whenever(orderGateway.isMerchantOrder(orderId, merchantId, branchId)).thenReturn(true)
        whenever(invoiceProvider.findOrCreate(orderId)).thenReturn(null)

        val response = controller.getInvoice(orderId, merchantId, branchId)

        assertEquals(404, response.statusCode.value())
        verify(storageService, never()).presignDownload(any(), any())
    }

    @Test
    fun `merchant id alone does not authorize invoice access`() {
        whenever(orderGateway.isMerchantOrder(orderId, merchantId, branchId)).thenReturn(true)
        assertEquals(403, controller.getInvoice(orderId, merchantId, branchId).statusCode.value())
        verifyNoInteractions(invoiceProvider, storageService)
    }
}
