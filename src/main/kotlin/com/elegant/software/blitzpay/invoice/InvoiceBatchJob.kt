package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice.DeliveryStatus
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoiceRepository
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderInvoiceSource
import com.elegant.software.blitzpay.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.io.ByteArrayResource
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Renders missing order invoices and emails them to the shopper, a few per run.
 * Rendering is memory-heavy, so it runs here instead of on the payment path. Only active
 * when SMTP is configured (`spring.mail.host`); until the job has run, the invoice
 * endpoints generate the requested paid invoice on demand.
 */
@Component
@ConditionalOnProperty("spring.mail.host")
class InvoiceBatchJob(
    private val orderGateway: OrderGateway,
    private val storageService: StorageService,
    private val orderInvoiceRepository: OrderInvoiceRepository,
    private val mailSender: JavaMailSender,
    private val invoiceProvider: OrderInvoiceProvider,
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
        if (source.buyerEmail.isNullOrBlank()) return false
        return invoiceProvider.findOrCreate(orderId, paidOnly = false) != null
    }

    private fun emailPendingInvoices() {
        val pending = mutableListOf<Pair<OrderInvoice, OrderInvoiceSource?>>()
        var page = 0
        // Select before changing statuses: flushing SENT rows would shift subsequent pages.
        while (pending.size < batchSize) {
            val rows = orderInvoiceRepository.findByDeliveryStatusOrderByGeneratedAt(
                DeliveryStatus.PENDING, PageRequest.of(page++, batchSize, Sort.by("id")),
            )
            for (invoice in rows) {
                val source = orderGateway.findInvoiceSource(invoice.orderId)
                if (source != null && !source.paid) continue
                pending.add(invoice to source)
                if (pending.size == batchSize) break
            }
            if (rows.size < batchSize) break
        }
        for ((invoice, source) in pending) {
            val recipient = source?.buyerEmail
            if (recipient.isNullOrBlank()) {
                invoice.deliveryStatus = DeliveryStatus.SKIPPED
                log.info("invoice email skipped — no recipient email orderId={}", invoice.orderId)
                continue
            }
            runCatching {
                send(recipient, invoice)
                invoice.deliveryStatus = DeliveryStatus.SENT
                // The guest email's only purpose (sending this invoice) is fulfilled: data minimisation.
                orderGateway.clearGuestInvoiceEmail(invoice.orderId)
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

    companion object {
        /** Arbitrary constant identifying this job's Postgres advisory lock. */
        const val LOCK_KEY = 172_001L
    }
}
