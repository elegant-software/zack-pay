package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.STOREFRONT_GUEST_TOKEN_COOKIE
import com.elegant.software.blitzpay.storage.StorageService
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/storefront/orders", version = "1")
class StorefrontInvoiceController(
    private val orderGateway: OrderGateway,
    private val invoiceProvider: OrderInvoiceProvider,
    private val storageService: StorageService,
) {
    @Operation(summary = "Get a 30-minute invoice PDF link for a paid guest order")
    @GetMapping("/{orderId}/invoice")
    fun getInvoice(
        @PathVariable orderId: String,
        @CookieValue(name = STOREFRONT_GUEST_TOKEN_COOKIE, required = false) token: String?,
    ): ResponseEntity<InvoiceDownloadResponse> {
        if (!orderGateway.isGuestTokenValid(orderId, token)) {
            throw ApiException(HttpStatus.NOT_FOUND, "Order not found")
        }
        val invoice = invoiceProvider.findOrCreate(orderId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "Paid invoice is not available yet")
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
            InvoiceDownloadResponse(storageService.presignDownload(invoice.s3Key, ttlMinutes = 30), "invoice-$orderId.pdf")
        )
    }
}
