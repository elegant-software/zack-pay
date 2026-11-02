package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceLineItem
import com.elegant.software.blitzpay.invoice.api.InvoiceService
import com.elegant.software.blitzpay.invoice.api.TradePartyData
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice.DeliveryStatus
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoiceRepository
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceGateway
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceProfile
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderInvoiceSource
import com.elegant.software.blitzpay.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ByteArrayResource
import org.springframework.data.domain.PageRequest
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64

/**
 * Renders missing order invoices and emails them to the shopper, a few per run.
 * Rendering is memory-heavy, so it runs here instead of on the payment path. Only active
 * when SMTP is configured (`spring.mail.host`); until the job has run, the invoice
 * endpoints answer 404.
 */
@Component
@ConditionalOnProperty("spring.mail.host")
class InvoiceBatchJob(
    private val orderGateway: OrderGateway,
    private val merchantInvoiceGateway: MerchantInvoiceGateway,
    private val invoiceService: InvoiceService,
    private val storageService: StorageService,
    private val orderInvoiceRepository: OrderInvoiceRepository,
    private val mailSender: JavaMailSender,
    @Value("\${blitzpay.invoice.email.from}") private val fromAddress: String,
    @Value("\${blitzpay.invoice.batch.lookback-days:7}") private val lookbackDays: Long,
    @Value("\${blitzpay.invoice.batch.size:20}") private val batchSize: Int,
) {
    private val log = LoggerFactory.getLogger(InvoiceBatchJob::class.java)

    @Scheduled(
        initialDelayString = "\${blitzpay.invoice.batch.initial-delay-ms:60000}",
        fixedDelayString = "\${blitzpay.invoice.batch.interval-ms:300000}",
    )
    @Transactional
    fun run() {
        // Held until this transaction ends, so only one replica renders and emails at a time.
        if (!orderInvoiceRepository.tryAdvisoryXactLock(LOCK_KEY)) {
            log.debug("invoice batch skipped — another instance holds the lock")
            return
        }
        generateMissingInvoices()
        emailPendingInvoices()
    }

    private fun generateMissingInvoices() {
        val since = Instant.now().minus(Duration.ofDays(lookbackDays))
        val existing = orderInvoiceRepository.findOrderIdsGeneratedAfter(since).toSet()
        val missing = orderGateway.findInvoiceableOrderIds(since).filterNot { it in existing }
        var rendered = 0
        for (orderId in missing) {
            if (rendered >= batchSize) break
            runCatching { if (generate(orderId)) rendered++ }
                .onFailure { log.error("invoice generation failed orderId={}", orderId, it) }
        }
        if (rendered > 0) log.info("invoice batch generated {} invoice(s)", rendered)
    }

    private fun generate(orderId: String): Boolean {
        val source = orderGateway.findInvoiceSource(orderId) ?: return false
        if (!source.isInvoiceOrder) {
            val missingFields = missingBuyerContactFields(source)
            if (missingFields.isNotEmpty()) {
                log.debug("invoice skipped — missing buyer contact fields orderId={} missing={}", orderId, missingFields)
                return false
            }
        }
        val merchant = merchantInvoiceGateway.findForInvoice(source.merchantId)
        if (merchant == null) {
            log.warn("invoice skipped — merchant not found merchantId={} orderId={}", source.merchantId, orderId)
            return false
        }

        val pdfBytes = invoiceService.generatePdf(buildInvoiceData(source, merchant))
        val s3Key = "invoices/$orderId.pdf"
        storageService.upload(s3Key, "application/pdf", pdfBytes)
        orderInvoiceRepository.save(
            OrderInvoice(orderIdFk = source.orderIdFk, orderId = orderId, s3Key = s3Key, generatedAt = Instant.now())
        )
        log.info("invoice generated and stored orderId={} s3Key={}", orderId, s3Key)
        return true
    }

    private fun emailPendingInvoices() {
        val pending = orderInvoiceRepository.findByDeliveryStatusOrderByGeneratedAt(
            DeliveryStatus.PENDING, PageRequest.of(0, batchSize),
        )
        for (invoice in pending) {
            val recipient = orderGateway.findInvoiceSource(invoice.orderId)?.buyerEmail
            if (recipient.isNullOrBlank()) {
                invoice.deliveryStatus = DeliveryStatus.SKIPPED
                log.info("invoice email skipped — no recipient email orderId={}", invoice.orderId)
                continue
            }
            runCatching {
                send(recipient, invoice)
                invoice.deliveryStatus = DeliveryStatus.SENT
                log.info("invoice emailed orderId={}", invoice.orderId)
            }.onFailure {
                // Stays PENDING and is retried on the next run.
                log.warn("invoice email failed orderId={}", invoice.orderId, it)
            }
        }
    }

    private fun send(recipient: String, invoice: OrderInvoice) {
        val pdf = storageService.download(invoice.s3Key)
        val message = mailSender.createMimeMessage()
        MimeMessageHelper(message, true, "UTF-8").apply {
            setFrom(fromAddress)
            setTo(recipient)
            setSubject("Your invoice INV-${invoice.orderId}")
            setText("Hello,\n\nplease find attached the invoice for your order ${invoice.orderId}.\n\nThank you for your purchase.")
            addAttachment("invoice-${invoice.orderId}.pdf", ByteArrayResource(pdf), "application/pdf")
        }
        mailSender.send(message)
    }

    private fun missingBuyerContactFields(source: OrderInvoiceSource): String =
        buildList {
            if (source.buyerContactName == null) add("name")
            if (source.buyerContactStreet == null) add("street")
            if (source.buyerContactZip == null) add("zip")
            if (source.buyerContactCity == null) add("city")
            if (source.buyerContactCountry == null) add("country")
        }.joinToString(",")

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
                        unitPrice = item.effectiveUnitPriceMinor.toMajor(),
                        vatPercent = vatPercent,
                    )
                }
            } else {
                listOf(
                    InvoiceLineItem(
                        description = "Order ${source.orderId}",
                        quantity = BigDecimal.ONE,
                        unitPrice = source.totalAmountMinor.toMajor(),
                        vatPercent = vatPercent,
                    )
                )
            },
            logoBase64 = logoBase64,
        )
    }

    private fun Long.toMajor(): BigDecimal = toBigDecimal().movePointLeft(2).setScale(2, RoundingMode.HALF_UP)

    companion object {
        /** Arbitrary constant identifying this job's Postgres advisory lock. */
        const val LOCK_KEY = 172_001L
    }
}
