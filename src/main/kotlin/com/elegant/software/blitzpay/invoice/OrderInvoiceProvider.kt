package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceLineItem
import com.elegant.software.blitzpay.invoice.api.InvoiceService
import com.elegant.software.blitzpay.invoice.api.TradePartyData
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoiceRepository
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceGateway
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceProfile
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderInvoiceSource
import com.elegant.software.blitzpay.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

@Service
class OrderInvoiceProvider(
    private val orderGateway: OrderGateway,
    private val merchantInvoiceGateway: MerchantInvoiceGateway,
    private val invoiceService: InvoiceService,
    private val storageService: StorageService,
    private val orderInvoiceRepository: OrderInvoiceRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun findOrCreate(orderId: String, paidOnly: Boolean = true): OrderInvoice? {
        val source = orderGateway.findInvoiceSource(orderId) ?: return null
        if (paidOnly && !source.paid) return null
        orderInvoiceRepository.findByOrderId(orderId)?.let { return it }
        // Share the batch lock so replicas do not render the same invoice concurrently.
        if (!orderInvoiceRepository.tryAdvisoryXactLock(InvoiceBatchJob.LOCK_KEY)) return null
        orderInvoiceRepository.findByOrderId(orderId)?.let { return it }
        val merchant = merchantInvoiceGateway.findForInvoice(source.merchantId) ?: return null
        log.info("invoice generation started orderId={}", orderId)
        val pdfBytes = invoiceService.generatePdf(buildInvoiceData(source, merchant))
        val invoice = OrderInvoice(orderIdFk = source.orderIdFk, orderId = orderId,
            s3Key = "invoices/$orderId.pdf", generatedAt = Instant.now())
        storageService.upload(invoice.s3Key, "application/pdf", pdfBytes)
        orderInvoiceRepository.save(invoice)
        log.info("invoice generated and stored orderId={}", orderId)
        return invoice
    }

    private fun buildInvoiceData(source: OrderInvoiceSource, merchant: MerchantInvoiceProfile): InvoiceData {
        val issueDate = source.issuedAt.atZone(ZoneOffset.UTC).toLocalDate()
        val vatPercent = merchant.defaultVatPercent ?: BigDecimal.ZERO

        val logoBase64 = merchant.logoStorageKey?.let { key ->
            runCatching { Base64.getEncoder().encodeToString(storageService.download(key)) }
                .onFailure { log.warn("invoice logo fetch failed key={} orderId={}", key, source.orderId) }
                .getOrNull()
        }

        val buyer = if (source.buyerContactName != null) {
            TradePartyData(
                name = source.buyerContactName,
                street = source.buyerContactStreet ?: "",
                zip = source.buyerContactZip ?: "",
                city = source.buyerContactCity ?: "",
                country = source.buyerContactCountry ?: "",
            )
        } else {
            TradePartyData(name = "—", street = "", zip = "", city = "", country = "")
        }

        return InvoiceData(
            invoiceNumber = "INV-${source.orderId}",
            issueDate = issueDate,
            dueDate = if (source.isInvoiceOrder) source.dueDate ?: issueDate else issueDate,
            currency = source.currency,
            seller = TradePartyData(
                name = merchant.merchantName,
                street = merchant.addressLine1 ?: "",
                zip = merchant.postalCode ?: "",
                city = merchant.city ?: "",
                country = merchant.country ?: "",
                vatId = merchant.registrationNumber,
            ),
            buyer = buyer,
            lineItems = if (source.items.isNotEmpty()) {
                source.items.map { item ->
                    InvoiceLineItem(
                        description = item.productName,
                        quantity = item.quantity.toBigDecimal(),
                        unitPrice = item.effectiveUnitPriceMinor.toNet(vatPercent),
                        vatPercent = vatPercent,
                    )
                }
            } else {
                listOf(
                    InvoiceLineItem(
                        description = "Order ${source.orderId}",
                        quantity = BigDecimal.ONE,
                        unitPrice = source.totalAmountMinor.toNet(vatPercent),
                        vatPercent = vatPercent,
                    )
                )
            },
            logoBase64 = logoBase64,
        )
    }

    // Order prices are gross (German B2C, PAngV); the invoice generator adds VAT on top of net unit prices.
    private fun Long.toNet(vatPercent: BigDecimal): BigDecimal =
        toBigDecimal().movePointLeft(2)
            .divide(BigDecimal.ONE + vatPercent.movePointLeft(2), 4, RoundingMode.HALF_UP)

}
