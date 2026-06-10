package com.elegant.software.blitzpay.reporting

import com.elegant.software.blitzpay.jooq.Tables.PAYMENT_SETTLEMENTS
import org.jooq.impl.DSL
import org.jooq.SQLDialect
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

class PaymentSettlementQueryServiceTest {

    @Test
    fun `maps qualified settlement fields and aggregate aliases`() {
        val context = DSL.using(SQLDialect.POSTGRES)
        val row = context.newRecord(
            PAYMENT_SETTLEMENTS.CURRENCY,
            PAYMENT_SETTLEMENTS.PAYMENT_METHOD,
            TX_COUNT,
            TOTAL_GROSS,
            TOTAL_PROCESSING_FEE,
            TOTAL_PLATFORM_FEE,
            TOTAL_NET,
        ).apply {
            set(PAYMENT_SETTLEMENTS.CURRENCY, "EUR")
            set(PAYMENT_SETTLEMENTS.PAYMENT_METHOD, "CARD")
            set(TX_COUNT, 2)
            set(TOTAL_GROSS, BigDecimal.valueOf(2_000))
            set(TOTAL_PROCESSING_FEE, BigDecimal.valueOf(80))
            set(TOTAL_PLATFORM_FEE, BigDecimal.valueOf(20))
            set(TOTAL_NET, BigDecimal.valueOf(1_900))
        }

        val merchantId = UUID.randomUUID()
        val summary = mapSettlementSummary(
            merchantId = merchantId,
            from = LocalDate.parse("2026-05-31"),
            to = LocalDate.parse("2026-06-10"),
            rows = listOf(row),
        )

        assertEquals(merchantId, summary.merchantId)
        assertEquals("EUR", summary.currency)
        assertEquals(2, summary.transactionCount)
        assertEquals(2_000, summary.grossAmountMinor)
        assertEquals(80, summary.processingFeeMinor)
        assertEquals(20, summary.platformFeeMinor)
        assertEquals(1_900, summary.netAmountMinor)
        assertEquals("CARD", summary.byPaymentMethod.single().paymentMethod)
    }
}
