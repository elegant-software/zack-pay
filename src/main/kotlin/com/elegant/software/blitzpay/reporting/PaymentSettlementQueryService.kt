package com.elegant.software.blitzpay.reporting

import com.elegant.software.blitzpay.jooq.Tables.ORDER_ORDERS
import com.elegant.software.blitzpay.jooq.Tables.PAYMENT_SETTLEMENTS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL.sum
import org.jooq.impl.DSL.count
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal val TX_COUNT = count().`as`("tx_count")
internal val TOTAL_GROSS = sum(PAYMENT_SETTLEMENTS.GROSS_AMOUNT_MINOR).`as`("total_gross")
internal val TOTAL_PROCESSING_FEE = sum(PAYMENT_SETTLEMENTS.PROCESSING_FEE_MINOR).`as`("total_processing_fee")
internal val TOTAL_PLATFORM_FEE = sum(PAYMENT_SETTLEMENTS.PLATFORM_FEE_MINOR).`as`("total_platform_fee")
internal val TOTAL_NET = sum(PAYMENT_SETTLEMENTS.NET_AMOUNT_MINOR).`as`("total_net")

@Service
class PaymentSettlementQueryService(private val dsl: DSLContext) {

    fun summary(merchantId: UUID, from: LocalDate, to: LocalDate): SettlementSummaryResponse {
        val fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC)
        val toInstant = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)

        val rows = dsl
            .select(
                PAYMENT_SETTLEMENTS.CURRENCY,
                PAYMENT_SETTLEMENTS.PAYMENT_METHOD,
                TX_COUNT,
                TOTAL_GROSS,
                TOTAL_PROCESSING_FEE,
                TOTAL_PLATFORM_FEE,
                TOTAL_NET,
            )
            .from(PAYMENT_SETTLEMENTS)
            .join(ORDER_ORDERS).on(PAYMENT_SETTLEMENTS.ORDER_ID_FK.eq(ORDER_ORDERS.ID))
            .where(ORDER_ORDERS.MERCHANT_APPLICATION_ID.eq(merchantId))
            .and(PAYMENT_SETTLEMENTS.SETTLED_AT.ge(fromInstant.atOffset(ZoneOffset.UTC)))
            .and(PAYMENT_SETTLEMENTS.SETTLED_AT.lt(toInstant.atOffset(ZoneOffset.UTC)))
            .groupBy(PAYMENT_SETTLEMENTS.CURRENCY, PAYMENT_SETTLEMENTS.PAYMENT_METHOD)
            .fetch()

        return mapSettlementSummary(merchantId, from, to, rows)
    }

    fun list(merchantId: UUID, from: LocalDate, to: LocalDate, page: Int, size: Int): SettlementPageResponse {
        val fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC)
        val toInstant = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)

        val baseCondition = ORDER_ORDERS.MERCHANT_APPLICATION_ID.eq(merchantId)
            .and(PAYMENT_SETTLEMENTS.SETTLED_AT.ge(fromInstant.atOffset(ZoneOffset.UTC)))
            .and(PAYMENT_SETTLEMENTS.SETTLED_AT.lt(toInstant.atOffset(ZoneOffset.UTC)))

        val total = dsl
            .selectCount()
            .from(PAYMENT_SETTLEMENTS)
            .join(ORDER_ORDERS).on(PAYMENT_SETTLEMENTS.ORDER_ID_FK.eq(ORDER_ORDERS.ID))
            .where(baseCondition)
            .fetchOne(0, Long::class.java) ?: 0L

        val rows = dsl
            .select(
                PAYMENT_SETTLEMENTS.PAYMENT_REQUEST_ID,
                PAYMENT_SETTLEMENTS.ORDER_ID,
                PAYMENT_SETTLEMENTS.SETTLED_AT,
                PAYMENT_SETTLEMENTS.PROVIDER,
                PAYMENT_SETTLEMENTS.CURRENCY,
                PAYMENT_SETTLEMENTS.GROSS_AMOUNT_MINOR,
                PAYMENT_SETTLEMENTS.PROCESSING_FEE_MINOR,
                PAYMENT_SETTLEMENTS.PLATFORM_FEE_MINOR,
                PAYMENT_SETTLEMENTS.NET_AMOUNT_MINOR,
                PAYMENT_SETTLEMENTS.PAYMENT_METHOD,
                PAYMENT_SETTLEMENTS.PAYMENT_METHOD_DETAIL,
                PAYMENT_SETTLEMENTS.FUNDS_AVAILABLE_AT,
                PAYMENT_SETTLEMENTS.PROVIDER_PAYMENT_REFERENCE,
                PAYMENT_SETTLEMENTS.PROVIDER_CHARGE_REFERENCE,
                PAYMENT_SETTLEMENTS.PROVIDER_DASHBOARD_URL,
            )
            .from(PAYMENT_SETTLEMENTS)
            .join(ORDER_ORDERS).on(PAYMENT_SETTLEMENTS.ORDER_ID_FK.eq(ORDER_ORDERS.ID))
            .where(baseCondition)
            .orderBy(PAYMENT_SETTLEMENTS.SETTLED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch()

        val content = rows.map { row ->
            SettlementRow(
                paymentRequestId = row.get(PAYMENT_SETTLEMENTS.PAYMENT_REQUEST_ID)!!,
                orderId = row.get(PAYMENT_SETTLEMENTS.ORDER_ID) ?: "",
                settledAt = row.get(PAYMENT_SETTLEMENTS.SETTLED_AT)?.toInstant() ?: java.time.Instant.EPOCH,
                provider = row.get(PAYMENT_SETTLEMENTS.PROVIDER) ?: "",
                currency = row.get(PAYMENT_SETTLEMENTS.CURRENCY) ?: "",
                grossAmountMinor = row.get(PAYMENT_SETTLEMENTS.GROSS_AMOUNT_MINOR) ?: 0L,
                processingFeeMinor = row.get(PAYMENT_SETTLEMENTS.PROCESSING_FEE_MINOR) ?: 0L,
                platformFeeMinor = row.get(PAYMENT_SETTLEMENTS.PLATFORM_FEE_MINOR) ?: 0L,
                netAmountMinor = row.get(PAYMENT_SETTLEMENTS.NET_AMOUNT_MINOR) ?: 0L,
                paymentMethod = row.get(PAYMENT_SETTLEMENTS.PAYMENT_METHOD) ?: "",
                paymentMethodDetail = row.get(PAYMENT_SETTLEMENTS.PAYMENT_METHOD_DETAIL),
                fundsAvailableAt = row.get(PAYMENT_SETTLEMENTS.FUNDS_AVAILABLE_AT)?.toInstant(),
                providerPaymentReference = row.get(PAYMENT_SETTLEMENTS.PROVIDER_PAYMENT_REFERENCE),
                providerChargeReference = row.get(PAYMENT_SETTLEMENTS.PROVIDER_CHARGE_REFERENCE),
                providerDashboardUrl = row.get(PAYMENT_SETTLEMENTS.PROVIDER_DASHBOARD_URL),
            )
        }

        val totalPages = if (total == 0L) 0 else ((total + size - 1) / size).toInt()
        return SettlementPageResponse(
            content = content,
            page = page,
            size = size,
            totalElements = total,
            totalPages = totalPages,
        )
    }
}

internal fun mapSettlementSummary(
    merchantId: UUID,
    from: LocalDate,
    to: LocalDate,
    rows: Iterable<Record>,
): SettlementSummaryResponse {
    val rowList = rows.toList()
    val currency = rowList.firstOrNull()?.get(PAYMENT_SETTLEMENTS.CURRENCY) ?: "EUR"
    val totalCount = rowList.sumOf { it.get(TX_COUNT) ?: 0 }
    val totalGross = rowList.sumOf { it.get(TOTAL_GROSS)?.toLong() ?: 0L }
    val totalProcessing = rowList.sumOf { it.get(TOTAL_PROCESSING_FEE)?.toLong() ?: 0L }
    val totalPlatform = rowList.sumOf { it.get(TOTAL_PLATFORM_FEE)?.toLong() ?: 0L }
    val totalNet = rowList.sumOf { it.get(TOTAL_NET)?.toLong() ?: 0L }

    val breakdown = rowList.map { row ->
        PaymentMethodBreakdown(
            paymentMethod = row.get(PAYMENT_SETTLEMENTS.PAYMENT_METHOD) ?: "",
            transactionCount = row.get(TX_COUNT) ?: 0,
            grossAmountMinor = row.get(TOTAL_GROSS)?.toLong() ?: 0L,
            netAmountMinor = row.get(TOTAL_NET)?.toLong() ?: 0L,
        )
    }

    return SettlementSummaryResponse(
        merchantId = merchantId,
        from = from.toString(),
        to = to.toString(),
        currency = currency,
        transactionCount = totalCount,
        grossAmountMinor = totalGross,
        processingFeeMinor = totalProcessing,
        platformFeeMinor = totalPlatform,
        netAmountMinor = totalNet,
        byPaymentMethod = breakdown,
    )
}
