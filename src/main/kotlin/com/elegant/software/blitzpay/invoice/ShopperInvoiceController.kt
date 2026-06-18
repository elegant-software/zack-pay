package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.persistence.OrderInvoiceRepository
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.storage.StorageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.Base64
import java.util.UUID

@Tag(name = "Invoices", description = "EU-standard ZUGFeRD / Factur-X invoice generation")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/shopper/orders", version = "1")
class ShopperInvoiceController(
    private val orderInvoiceRepository: OrderInvoiceRepository,
    private val orderGateway: OrderGateway,
    private val storageService: StorageService,
) {

    @Operation(summary = "Get a presigned download URL for the invoice of a paid order")
    @GetMapping("/{orderId}/invoice")
    fun getInvoice(
        @PathVariable orderId: String,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<InvoiceDownloadResponse> {
        val shopperId = requireSubject(authorization)
        if (!orderGateway.isShopperOrder(orderId, shopperId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val invoice = orderInvoiceRepository.findByOrderId(orderId)
            ?: return ResponseEntity.notFound().build()
        val url = storageService.presignDownload(invoice.s3Key, ttlMinutes = 30)
        return ResponseEntity.ok(InvoiceDownloadResponse(url = url, filename = "invoice-$orderId.pdf"))
    }

    private fun requireSubject(authorization: String?): String =
        extractSubject(authorization) ?: throw org.springframework.web.server.ResponseStatusException(
            HttpStatus.UNAUTHORIZED, "Valid authentication is required"
        )

    private fun extractSubject(authorization: String?): String? {
        val token = authorization
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.removePrefix("Bearer ")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val parts = token.split('.')
        if (parts.size < 2) return null
        return runCatching {
            String(Base64.getUrlDecoder().decode(parts[1]))
        }.mapCatching { payload ->
            Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"").find(payload)?.groupValues?.getOrNull(1)
        }.getOrNull()?.takeIf { it.isNotBlank() }?.take(512)
    }
}

@Tag(name = "Invoices", description = "EU-standard ZUGFeRD / Factur-X invoice generation")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchant/orders", version = "1")
class MerchantInvoiceController(
    private val orderInvoiceRepository: OrderInvoiceRepository,
    private val orderGateway: OrderGateway,
    private val storageService: StorageService,
) {

    @Operation(summary = "Get a presigned download URL for a merchant order invoice")
    @GetMapping("/{orderId}/invoice")
    fun getInvoice(
        @PathVariable orderId: String,
        @RequestParam merchantId: UUID,
        @RequestParam(required = false) branchId: UUID?,
    ): ResponseEntity<InvoiceDownloadResponse> {
        if (!orderGateway.isMerchantOrder(orderId, merchantId, branchId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val invoice = orderInvoiceRepository.findByOrderId(orderId)
            ?: return ResponseEntity.notFound().build()
        val url = storageService.presignDownload(invoice.s3Key, ttlMinutes = 30)
        return ResponseEntity.ok(InvoiceDownloadResponse(url = url, filename = "invoice-$orderId.pdf"))
    }
}

data class InvoiceDownloadResponse(val url: String, val filename: String)
