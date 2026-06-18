package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoiceRepository
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.storage.StorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.test.assertNotNull

class MerchantInvoiceControllerTest {

    private val orderInvoiceRepository = mock<OrderInvoiceRepository>()
    private val orderGateway = mock<OrderGateway>()
    private val storageService = mock<StorageService>()
    private val controller = MerchantInvoiceController(orderInvoiceRepository, orderGateway, storageService)

    private val merchantId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val branchId = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val orderId = "ORD-PAID0000001"

    @Test
    fun `returns presigned invoice url for merchant-owned order`() {
        val invoice = OrderInvoice(
            orderIdFk = UUID.randomUUID(),
            orderId = orderId,
            s3Key = "invoices/$orderId.pdf",
            generatedAt = Instant.parse("2026-06-17T10:15:30Z"),
        )
        whenever(orderGateway.isMerchantOrder(orderId, merchantId, branchId)).thenReturn(true)
        whenever(orderInvoiceRepository.findByOrderId(orderId)).thenReturn(invoice)
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
        verify(orderInvoiceRepository, never()).findByOrderId(any())
        verify(storageService, never()).presignDownload(any(), any())
    }

    @Test
    fun `returns not found when owned order has no invoice`() {
        whenever(orderGateway.isMerchantOrder(orderId, merchantId, branchId)).thenReturn(true)
        whenever(orderInvoiceRepository.findByOrderId(orderId)).thenReturn(null)

        val response = controller.getInvoice(orderId, merchantId, branchId)

        assertEquals(404, response.statusCode.value())
        verify(storageService, never()).presignDownload(any(), any())
    }
}
