package com.elegant.software.blitzpay.payments.invoice

import com.elegant.software.blitzpay.invoice.InvoiceController
import com.elegant.software.blitzpay.invoice.api.InvoiceService
import com.elegant.software.blitzpay.support.TestFixtureLoader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InvoiceControllerTest {

    private val invoiceService = mock<InvoiceService>()
    private val controller = InvoiceController(invoiceService)

    private val scenario = TestFixtureLoader.invoiceScenario()
    private val expectations = scenario.expectations

    @Test
    fun `generateXml returns XML with correct content type and disposition`() {
        val xmlBytes = "<CrossIndustryInvoice>test</CrossIndustryInvoice>".toByteArray()
        whenever(invoiceService.generateXml(eq(TestFixtureLoader.invoiceData()))).thenReturn(xmlBytes)

        val response = controller.generateXml(TestFixtureLoader.invoiceData())

        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_XML, response.headers.contentType)
        assertEquals(
            "attachment; filename=\"invoice-${expectations.invoiceNumber}.xml\"",
            response.headers.getFirst("Content-Disposition"),
        )
        assertNotNull(response.body)

        verify(invoiceService).generateXml(eq(TestFixtureLoader.invoiceData()))
    }

    @Test
    fun `generatePdf returns PDF with correct content type and disposition`() {
        val pdfBytes = "%PDF-1.4 fake content".toByteArray()
        whenever(invoiceService.generatePdf(eq(TestFixtureLoader.invoiceData()))).thenReturn(pdfBytes)

        val response = controller.generatePdf(TestFixtureLoader.invoiceData())

        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_PDF, response.headers.contentType)
        assertEquals(
            "attachment; filename=\"invoice-${expectations.invoiceNumber}.pdf\"",
            response.headers.getFirst("Content-Disposition"),
        )
        assertTrue(response.body!!.isNotEmpty(), "PDF response body must not be empty")

        verify(invoiceService).generatePdf(eq(TestFixtureLoader.invoiceData()))
    }
}
