package com.elegant.software.blitzpay.reporting

import com.elegant.software.blitzpay.jooq.Tables.APPOINTMENTS
import com.elegant.software.blitzpay.jooq.Tables.MERCHANTS
import com.elegant.software.blitzpay.jooq.Tables.MERCHANT_BRANCHES
import com.elegant.software.blitzpay.jooq.Tables.ORDER_ORDERS
import com.elegant.software.blitzpay.jooq.Tables.PAYMENT_SETTLEMENTS
import org.jooq.DSLContext
import org.jooq.impl.DSL.count
import org.jooq.impl.DSL.inline
import org.jooq.impl.DSL.sum
import org.jooq.impl.DSL.`when`
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
class DashboardQueryService(private val dsl: DSLContext) {

    fun platformKpi(from: LocalDate, to: LocalDate, merchantId: UUID?): PlatformKpiReport {
        val fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC)
        val toInstant = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)

        val revenue = queryRevenue(fromInstant, to, toInstant, merchantId, branchId = null)
        val pmFrequency = queryPaymentMethodFrequency(fromInstant, toInstant, merchantId, branchId = null)
        val orders = queryOrders(fromInstant, toInstant, merchantId, branchId = null)
        val appointments = queryAppointments(fromInstant, toInstant, merchantId, branchId = null)

        val (merchantCounts, branchCounts) = if (merchantId == null) {
            queryMerchantCounts(fromInstant, toInstant) to queryBranchCounts(fromInstant, toInstant)
        } else {
            null to null
        }

        return PlatformKpiReport(
            from = from.toString(),
            to = to.toString(),
            merchantId = merchantId,
            revenue = revenue,
            orders = orders,
            appointments = appointments,
            paymentMethodFrequency = pmFrequency,
            merchantCounts = merchantCounts,
            branchCounts = branchCounts,
        )
    }

    fun merchantKpi(merchantId: UUID, branchId: UUID?, from: LocalDate, to: LocalDate): MerchantKpiReport {
        val fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC)
        val toInstant = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)

        val revenue = queryRevenue(fromInstant, to, toInstant, merchantId, branchId)
        val pmFrequency = queryPaymentMethodFrequency(fromInstant, toInstant, merchantId, branchId)
        val orders = queryOrders(fromInstant, toInstant, merchantId, branchId)
        val appointments = queryAppointments(fromInstant, toInstant, merchantId, branchId)

        return MerchantKpiReport(
            merchantId = merchantId,
            branchId = branchId,
            from = from.toString(),
            to = to.toString(),
            revenue = revenue,
            orders = orders,
            appointments = appointments,
            paymentMethodFrequency = pmFrequency,
        )
    }

    private fun queryRevenue(
        fromInstant: java.time.Instant,
        to: LocalDate,
        toInstant: java.time.Instant,
        merchantId: UUID?,
        branchId: UUID?,
    ): RevenueKpi {
        val fromOffset = fromInstant.atOffset(ZoneOffset.UTC)
        val toOffset = toInstant.atOffset(ZoneOffset.UTC)

        var query = dsl
            .select(
                PAYMENT_SETTLEMENTS.CURRENCY,
                count().`as`("tx_count"),
                sum(PAYMENT_SETTLEMENTS.GROSS_AMOUNT_MINOR).`as`("total_gross"),
                sum(PAYMENT_SETTLEMENTS.PROCESSING_FEE_MINOR).`as`("total_processing_fee"),
                sum(PAYMENT_SETTLEMENTS.PLATFORM_FEE_MINOR).`as`("total_platform_fee"),
                sum(PAYMENT_SETTLEMENTS.NET_AMOUNT_MINOR).`as`("total_net"),
            )
            .from(PAYMENT_SETTLEMENTS)
            .join(ORDER_ORDERS).on(PAYMENT_SETTLEMENTS.ORDER_ID_FK.eq(ORDER_ORDERS.ID))
            .where(PAYMENT_SETTLEMENTS.SETTLED_AT.ge(fromOffset))
            .and(PAYMENT_SETTLEMENTS.SETTLED_AT.lt(toOffset))

        if (merchantId != null) query = query.and(ORDER_ORDERS.MERCHANT_APPLICATION_ID.eq(merchantId))
        if (branchId != null) query = query.and(ORDER_ORDERS.MERCHANT_BRANCH_ID.eq(branchId))

        val row = query
            .groupBy(PAYMENT_SETTLEMENTS.CURRENCY)
            .fetchOne()

        return RevenueKpi(
            currency = row?.get(PAYMENT_SETTLEMENTS.CURRENCY) ?: "EUR",
            grossAmountMinor = row?.get("total_gross", Long::class.java) ?: 0L,
            processingFeeMinor = row?.get("total_processing_fee", Long::class.java) ?: 0L,
            platformFeeMinor = row?.get("total_platform_fee", Long::class.java) ?: 0L,
            netAmountMinor = row?.get("total_net", Long::class.java) ?: 0L,
            settledTransactionCount = row?.get("tx_count", Int::class.java) ?: 0,
        )
    }

    private fun queryPaymentMethodFrequency(
        fromInstant: java.time.Instant,
        toInstant: java.time.Instant,
        merchantId: UUID?,
        branchId: UUID?,
    ): List<PaymentMethodFrequency> {
        val fromOffset = fromInstant.atOffset(ZoneOffset.UTC)
        val toOffset = toInstant.atOffset(ZoneOffset.UTC)

        var query = dsl
            .select(
                PAYMENT_SETTLEMENTS.PAYMENT_METHOD,
                count().`as`("tx_count"),
            )
            .from(PAYMENT_SETTLEMENTS)
            .join(ORDER_ORDERS).on(PAYMENT_SETTLEMENTS.ORDER_ID_FK.eq(ORDER_ORDERS.ID))
            .where(PAYMENT_SETTLEMENTS.SETTLED_AT.ge(fromOffset))
            .and(PAYMENT_SETTLEMENTS.SETTLED_AT.lt(toOffset))

        if (merchantId != null) query = query.and(ORDER_ORDERS.MERCHANT_APPLICATION_ID.eq(merchantId))
        if (branchId != null) query = query.and(ORDER_ORDERS.MERCHANT_BRANCH_ID.eq(branchId))

        val rows = query
            .groupBy(PAYMENT_SETTLEMENTS.PAYMENT_METHOD)
            .orderBy(count().desc())
            .fetch()

        val total = rows.sumOf { it.get("tx_count", Int::class.java) ?: 0 }.toDouble()
        return rows.map { row ->
            val count = row.get("tx_count", Int::class.java) ?: 0
            PaymentMethodFrequency(
                paymentMethod = row.get(PAYMENT_SETTLEMENTS.PAYMENT_METHOD) ?: "",
                transactionCount = count,
                sharePercent = if (total > 0) (count / total) * 100.0 else 0.0,
            )
        }
    }

    private fun queryOrders(
        fromInstant: java.time.Instant,
        toInstant: java.time.Instant,
        merchantId: UUID?,
        branchId: UUID?,
    ): OrderKpi {
        val fromOffset = fromInstant.atOffset(ZoneOffset.UTC)
        val toOffset = toInstant.atOffset(ZoneOffset.UTC)

        var query = dsl
            .select(
                ORDER_ORDERS.STATUS,
                ORDER_ORDERS.ORDER_TYPE,
                count().`as`("cnt"),
            )
            .from(ORDER_ORDERS)
            .where(ORDER_ORDERS.CREATED_AT.ge(fromOffset))
            .and(ORDER_ORDERS.CREATED_AT.lt(toOffset))

        if (merchantId != null) query = query.and(ORDER_ORDERS.MERCHANT_APPLICATION_ID.eq(merchantId))
        if (branchId != null) query = query.and(ORDER_ORDERS.MERCHANT_BRANCH_ID.eq(branchId))

        val rows = query
            .groupBy(ORDER_ORDERS.STATUS, ORDER_ORDERS.ORDER_TYPE)
            .fetch()

        var paid = 0L
        var pending = 0L
        var failedOrCancelled = 0L
        val byOrderType = mutableMapOf<String, Long>()

        for (row in rows) {
            val status = row.get(ORDER_ORDERS.STATUS) ?: continue
            val orderType = row.get(ORDER_ORDERS.ORDER_TYPE) ?: "UNKNOWN"
            val cnt = row.get("cnt", Long::class.java) ?: 0L

            when (status) {
                "PAID" -> paid += cnt
                "CREATED", "PAYMENT_INITIATED" -> pending += cnt
                "FAILED", "CANCELLED" -> failedOrCancelled += cnt
            }

            byOrderType[orderType] = (byOrderType[orderType] ?: 0L) + cnt
        }

        return OrderKpi(
            paidCount = paid,
            pendingCount = pending,
            failedOrCancelledCount = failedOrCancelled,
            byOrderType = byOrderType.map { (type, count) -> OrderTypeCount(type, count) },
        )
    }

    private fun queryAppointments(
        fromInstant: java.time.Instant,
        toInstant: java.time.Instant,
        merchantId: UUID?,
        branchId: UUID?,
    ): AppointmentKpi {
        val fromOffset = fromInstant.atOffset(ZoneOffset.UTC)
        val toOffset = toInstant.atOffset(ZoneOffset.UTC)

        var query = dsl
            .select(
                APPOINTMENTS.STATUS,
                count().`as`("cnt"),
            )
            .from(APPOINTMENTS)
            .where(APPOINTMENTS.CREATED_AT.ge(fromOffset))
            .and(APPOINTMENTS.CREATED_AT.lt(toOffset))

        if (merchantId != null) query = query.and(APPOINTMENTS.MERCHANT_ID.eq(merchantId))
        if (branchId != null) query = query.and(APPOINTMENTS.BRANCH_ID.eq(branchId))

        val rows = query
            .groupBy(APPOINTMENTS.STATUS)
            .fetch()

        var booked = 0L
        var completed = 0L
        var cancelled = 0L

        for (row in rows) {
            val cnt = row.get("cnt", Long::class.java) ?: 0L
            when (row.get(APPOINTMENTS.STATUS)) {
                "BOOKED" -> booked = cnt
                "COMPLETED" -> completed = cnt
                "CANCELLED" -> cancelled = cnt
            }
        }

        return AppointmentKpi(bookedCount = booked, completedCount = completed, cancelledCount = cancelled)
    }

    private fun queryMerchantCounts(fromInstant: java.time.Instant, toInstant: java.time.Instant): MerchantCountKpi {
        val fromOffset = fromInstant.atOffset(ZoneOffset.UTC)
        val toOffset = toInstant.atOffset(ZoneOffset.UTC)

        val row = dsl
            .select(
                count().`as`("total"),
                count(`when`(MERCHANTS.MERCHANT_STATUS.eq("ACTIVE"), inline(1))).`as`("active"),
                count(`when`(
                    MERCHANTS.CREATED_AT.ge(fromOffset)
                        .and(MERCHANTS.CREATED_AT.lt(toOffset)),
                    inline(1),
                )).`as`("new_in_period"),
            )
            .from(MERCHANTS)
            .fetchOne()

        return MerchantCountKpi(
            totalCount = row?.get("total", Long::class.java) ?: 0L,
            activeCount = row?.get("active", Long::class.java) ?: 0L,
            newInPeriodCount = row?.get("new_in_period", Long::class.java) ?: 0L,
        )
    }

    private fun queryBranchCounts(fromInstant: java.time.Instant, toInstant: java.time.Instant): BranchCountKpi {
        val fromOffset = fromInstant.atOffset(ZoneOffset.UTC)
        val toOffset = toInstant.atOffset(ZoneOffset.UTC)

        val row = dsl
            .select(
                count().`as`("total"),
                count(`when`(MERCHANT_BRANCHES.ACTIVE.isTrue, inline(1))).`as`("active"),
                count(`when`(
                    MERCHANT_BRANCHES.CREATED_AT.ge(fromOffset)
                        .and(MERCHANT_BRANCHES.CREATED_AT.lt(toOffset)),
                    inline(1),
                )).`as`("new_in_period"),
            )
            .from(MERCHANT_BRANCHES)
            .fetchOne()

        return BranchCountKpi(
            totalCount = row?.get("total", Long::class.java) ?: 0L,
            activeCount = row?.get("active", Long::class.java) ?: 0L,
            newInPeriodCount = row?.get("new_in_period", Long::class.java) ?: 0L,
        )
    }
}
