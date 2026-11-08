package com.elegant.software.blitzpay.support

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.elegant.software.blitzpay.invoice.api.BankAccountData
import com.elegant.software.blitzpay.invoice.api.InvoiceData
import com.elegant.software.blitzpay.invoice.api.InvoiceLineItem
import java.math.BigDecimal

object TestFixtureLoader {

    private val objectMapper: JsonMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .addModule(JavaTimeModule())
        .propertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()

    val fixture: InvoiceFixtureScenario by lazy {
        val resource = requireNotNull(this::class.java.classLoader.getResourceAsStream(FIXTURE_PATH)) {
            "Missing fixture file: $FIXTURE_PATH"
        }
        resource.use { objectMapper.readValue(it) }
    }

    fun invoiceScenario(): InvoiceFixtureScenario = fixture

    fun invoiceRequestJson(): String = objectMapper.writeValueAsString(fixture.inputData)

    fun invoiceData(): InvoiceData = fixture.inputData

    fun withBankAccount(): InvoiceData = invoiceData().copy(
        bankAccount = BankAccountData(
            bankName = fixture.expectations.bankName,
            iban = fixture.expectations.iban,
            bic = fixture.expectations.bic
        )
    )

    fun withFooter(): InvoiceData = invoiceData().copy(
        footerText = fixture.expectations.footerText
    )

    fun withLogo(): InvoiceData = invoiceData().copy(
        logoBase64 = fixture.expectations.logoBase64
    )

    fun singleLineItem(): InvoiceData = invoiceData().copy(
        lineItems = listOf(
            InvoiceLineItem(
                description = fixture.expectations.singleLineItemDescription,
                quantity = BigDecimal(fixture.expectations.singleLineItemQuantity),
                unitPrice = BigDecimal(fixture.expectations.singleLineItemUnitPrice),
                vatPercent = BigDecimal(fixture.expectations.singleLineItemVatPercent)
            )
        )
    )

    private const val FIXTURE_PATH = "testdata/invoice/canonical-invoice.json"
}

data class InvoiceFixtureScenario(
    val scenarioId: String,
    val description: String,
    val domain: String,
    val tags: List<String>,
    val inputData: InvoiceData,
    val expectations: InvoiceExpectations,
)

data class InvoiceExpectations(
    val xmlRootElement: String,
    val invoiceNumber: String,
    val sellerName: String,
    val buyerName: String,
    val firstLineItemDescription: String,
    val secondLineItemDescription: String,
    val currency: String,
    val bankName: String,
    val iban: String,
    val bic: String,
    val footerText: String,
    val logoBase64: String,
    val singleLineItemDescription: String,
    val singleLineItemQuantity: String,
    val singleLineItemUnitPrice: String,
    val singleLineItemVatPercent: String,
)
