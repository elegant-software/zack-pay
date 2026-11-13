package com.elegant.software.blitzpay.invoice

import com.elegant.software.blitzpay.invoice.persistence.OrderInvoice
import com.elegant.software.blitzpay.invoice.persistence.OrderInvoiceRepository
import com.elegant.software.blitzpay.order.api.OrderGateway
import com.elegant.software.blitzpay.order.api.OrderInvoiceSource
import com.elegant.software.blitzpay.storage.StorageService
import com.elegant.software.blitzpay.support.TestFixtureLoader
import jakarta.mail.Multipart
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.net.InetAddress
import java.net.ServerSocket
import java.time.Instant
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class InvoiceEmailDeliveryTest {
    @Test
    fun `consenting paid guest receives an invoice PDF over SMTP and email is then deleted`() {
        val source = TestFixtureLoader.load("testdata/invoice/paid-download.json", OrderInvoiceSource::class.java)
            .copy(buyerEmail = "guest@example.test")
        val orders = mock<OrderGateway>()
        val invoices = mock<OrderInvoiceRepository>()
        val storage = mock<StorageService>()
        val invoice = OrderInvoice(orderIdFk = source.orderIdFk, orderId = source.orderId,
            s3Key = "invoices/${source.orderId}.pdf", generatedAt = Instant.parse("2026-11-11T12:00:00Z"))
        val pdf = "%PDF-1.7 test attachment".toByteArray()
        whenever(invoices.tryAdvisoryXactLock(InvoiceBatchJob.LOCK_KEY)).thenReturn(true)
        whenever(invoices.findByDeliveryStatusOrderByGeneratedAt(eq(OrderInvoice.DeliveryStatus.PENDING), any()))
            .thenReturn(listOf(invoice))
        whenever(orders.findInvoiceSource(source.orderId)).thenReturn(source)
        whenever(storage.download(invoice.s3Key)).thenReturn(pdf)

        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 5000
            Executors.newSingleThreadExecutor().use { executor ->
                val received = executor.submit<String> {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val input = socket.getInputStream().bufferedReader()
                        val output = socket.getOutputStream().bufferedWriter()
                        fun reply(line: String) { output.write("$line\r\n"); output.flush() }
                        reply("220 localhost test SMTP")
                        val message = StringBuilder()
                        while (true) {
                            val line = input.readLine() ?: break
                            when {
                                line.startsWith("EHLO") || line.startsWith("HELO") -> reply("250 localhost")
                                line == "DATA" -> {
                                    reply("354 End with a dot")
                                    while (true) {
                                        val data = input.readLine() ?: error("Unexpected end of SMTP data")
                                        if (data == ".") break
                                        message.append(if (data.startsWith("..")) data.drop(1) else data).append("\r\n")
                                    }
                                    reply("250 Message accepted")
                                }
                                line == "QUIT" -> { reply("221 Bye"); break }
                                else -> reply("250 OK")
                            }
                        }
                        message.toString()
                    }
                }
                val sender = JavaMailSenderImpl().apply { host = server.inetAddress.hostAddress; port = server.localPort }
                InvoiceBatchJob(orders, storage, invoices, sender, mock(), "invoices@example.test", 7, 20).run()
                val raw = received.get(5, TimeUnit.SECONDS)
                val delivered = MimeMessage(Session.getInstance(Properties()), raw.byteInputStream())
                assertEquals("guest@example.test", delivered.allRecipients.single().toString())
                assertEquals("Your invoice INV-${source.orderId}", delivered.subject)
                val parts = delivered.content as Multipart
                val attachment = (0 until parts.count).map { parts.getBodyPart(it) }.single { it.fileName != null }
                assertEquals("invoice-${source.orderId}.pdf", attachment.fileName)
                assertContentEquals(pdf, attachment.inputStream.readBytes())
                assertEquals(OrderInvoice.DeliveryStatus.SENT, invoice.deliveryStatus)
                verify(orders).clearGuestInvoiceEmail(source.orderId)
            }
        }
    }
}
