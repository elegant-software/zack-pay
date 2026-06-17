package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.api.BankAccountData
import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceLineItem
import com.elegant.software.blitzpay.invoice.api.InvoiceService
import com.elegant.software.blitzpay.invoice.api.TradePartyData
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoiceRepository
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceGateway
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceProfile
import com.elegant.software.blitzpay.order.api.OrderPaid
import com.elegant.software.blitzpay.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.util.Base64

@Component
@Profile("!contract-test")
class InvoiceOrchestrationListener(
    private val merchantInvoiceGateway: MerchantInvoiceGateway,
    private val invoiceService: InvoiceService,
    private val storageService: StorageService,
    private val orderInvoiceRepository: OrderInvoiceRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ApplicationModuleListener
    fun on(event: OrderPaid) {
        log.info("invoice processing started orderId={} merchantId={}", event.orderId, event.merchantId)

        val missingFields = missingBuyerContactFields(event)
        if (missingFields.isNotEmpty()) {
            log.info("invoice skipped — missing buyer contact fields orderId={} missing={}", event.orderId, missingFields)
            return
        }

        if (orderInvoiceRepository.existsByOrderId(event.orderId)) {
            log.info("invoice skipped — already exists orderId={}", event.orderId)
            return
        }

        val merchant = merchantInvoiceGateway.findForInvoice(event.merchantId)
        if (merchant == null) {
            log.warn("invoice skipped — merchant not found merchantId={} orderId={}", event.merchantId, event.orderId)
            return
        }

        val invoiceData = buildInvoiceData(event, merchant)
        val pdfBytes = invoiceService.generatePdf(invoiceData)

        val s3Key = "invoices/${event.orderId}.pdf"
        storageService.upload(s3Key, "application/pdf", pdfBytes)

        orderInvoiceRepository.save(
            OrderInvoice(
                orderIdFk = event.orderIdFk,
                orderId = event.orderId,
                s3Key = s3Key,
                generatedAt = Instant.now(),
            )
        )
        log.info("invoice generated and stored orderId={} s3Key={}", event.orderId, s3Key)
    }

    private fun missingBuyerContactFields(event: OrderPaid): String =
        buildList {
            if (event.buyerContactName == null) add("name")
            if (event.buyerContactStreet == null) add("street")
            if (event.buyerContactZip == null) add("zip")
            if (event.buyerContactCity == null) add("city")
            if (event.buyerContactCountry == null) add("country")
        }.joinToString(",")

    private fun buildInvoiceData(event: OrderPaid, merchant: MerchantInvoiceProfile): InvoiceData {
        val issueDate = event.paidAt.atZone(ZoneId.of("UTC")).toLocalDate()
        val vatPercent = merchant.defaultVatPercent ?: BigDecimal.ZERO
        val grossMajor = event.totalAmountMinor.toBigDecimal().movePointLeft(2).setScale(2, RoundingMode.HALF_UP)

        val logoBase64 = merchant.logoStorageKey?.let { key ->
            runCatching {
                Base64.getEncoder().encodeToString(storageService.download(key))
            }.onFailure {
                log.warn("invoice logo fetch failed key={} orderId={}", key, event.orderId)
            }.getOrNull()
        }

        return InvoiceData(
            invoiceNumber = "INV-${event.orderId}",
            issueDate = issueDate,
            dueDate = issueDate,
            currency = event.currency,
            seller = TradePartyData(
                name = merchant.merchantName,
                street = merchant.addressLine1 ?: "",
                zip = merchant.postalCode ?: "",
                city = merchant.city ?: "",
                country = merchant.country ?: "",
                vatId = merchant.registrationNumber,
            ),
            buyer = TradePartyData(
                name = event.buyerContactName!!,
                street = event.buyerContactStreet!!,
                zip = event.buyerContactZip!!,
                city = event.buyerContactCity!!,
                country = event.buyerContactCountry!!,
            ),
            lineItems = if (event.items.isNotEmpty()) {
                event.items.map { item ->
                    InvoiceLineItem(
                        description = item.productName,
                        quantity = item.quantity.toBigDecimal(),
                        unitPrice = item.effectiveUnitPriceMinor.toBigDecimal().movePointLeft(2).setScale(2, RoundingMode.HALF_UP),
                        vatPercent = vatPercent,
                    )
                }
            } else {
                listOf(
                    InvoiceLineItem(
                        description = "Order ${event.orderId}",
                        quantity = BigDecimal.ONE,
                        unitPrice = grossMajor,
                        vatPercent = vatPercent,
                    )
                )
            },
            logoBase64 = logoBase64,
        )
    }
}
