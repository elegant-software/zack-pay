package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice
import com.elegant.software.blitzpay.order.api.STOREFRONT_GUEST_TOKEN_COOKIE
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.util.UUID

class StorefrontInvoiceContractTest : ContractVerifierBase() {
    @MockitoBean private lateinit var invoiceProvider: OrderInvoiceProvider
    private val orderId = "ORD-INVOICE001"

    @Test
    fun `guest can obtain a paid invoice link without a Keycloak account`() {
        whenever(orderGateway.isGuestTokenValid(orderId, "order-cookie")).thenReturn(true)
        whenever(invoiceProvider.findOrCreate(orderId)).thenReturn(OrderInvoice(
            orderIdFk = UUID.fromString("11111111-1111-1111-1111-111111111111"), orderId = orderId,
            s3Key = "invoices/$orderId.pdf", generatedAt = Instant.parse("2026-11-11T12:00:00Z")))
        whenever(storageService.presignDownload("invoices/$orderId.pdf", 30)).thenReturn("https://storage.example/invoice.pdf")

        webTestClient.get().uri("/v1/storefront/orders/$orderId/invoice")
            .cookie(STOREFRONT_GUEST_TOKEN_COOKIE, "order-cookie").exchange()
            .expectStatus().isOk.expectHeader().valueEquals("Cache-Control", "no-store")
            .expectBody().jsonPath("$.url").isEqualTo("https://storage.example/invoice.pdf")
            .jsonPath("$.filename").isEqualTo("invoice-$orderId.pdf")
    }

    @Test
    fun `missing or foreign guest cookie cannot generate or disclose an invoice`() {
        webTestClient.get().uri("/v1/storefront/orders/$orderId/invoice").exchange()
            .expectStatus().isNotFound.expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND")
        webTestClient.get().uri("/v1/storefront/orders/$orderId/invoice")
            .cookie(STOREFRONT_GUEST_TOKEN_COOKIE, "another-order-cookie").exchange()
            .expectStatus().isNotFound
        verifyNoInteractions(invoiceProvider)
    }

    @Test
    fun `unavailable paid invoice has a retryable not found response`() {
        whenever(orderGateway.isGuestTokenValid(orderId, "order-cookie")).thenReturn(true)
        webTestClient.get().uri("/v1/storefront/orders/$orderId/invoice")
            .cookie(STOREFRONT_GUEST_TOKEN_COOKIE, "order-cookie").exchange()
            .expectStatus().isNotFound.expectBody().jsonPath("$.code").isEqualTo("NOT_FOUND")
        verifyNoInteractions(storageService)
    }

    @Test
    fun `authenticated shopper cannot obtain a merchant invoice by supplying its merchant id`() {
        val merchantId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        whenever(orderGateway.isMerchantOrder(orderId, merchantId, null)).thenReturn(true)
        whenever(invoiceProvider.findOrCreate(orderId)).thenReturn(OrderInvoice(
            orderIdFk = UUID.fromString("11111111-1111-1111-1111-111111111111"), orderId = orderId,
            s3Key = "invoices/$orderId.pdf", generatedAt = Instant.parse("2026-11-11T12:00:00Z")))
        whenever(storageService.presignDownload("invoices/$orderId.pdf", 30)).thenReturn("https://storage.example/invoice.pdf")

        webTestClient.get().uri("/v1/merchant/orders/$orderId/invoice?merchantId=$merchantId")
            .headers { it.setBearerAuth(fakeJwt) }.exchange().expectStatus().isForbidden
    }
}
