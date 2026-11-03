package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Hands out the next Pickup Number of a Branch for the Branch-local day (CONTEXT.md "Pickup Number").
 *
 * The upsert row-locks the (branch, day) counter until the surrounding order-creation transaction commits,
 * so concurrent orders of one Branch are serialised and never share a number; a new day starts a new row at 1.
 */
@Component
class PickupNumberAllocator(
    private val entityManager: EntityManager,
    private val merchantGateway: MerchantGateway,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun next(branchId: UUID): Int {
        val day = businessDay(branchId)
        entityManager.createNativeQuery(
            """
            INSERT INTO blitzpay.order_pickup_counters (branch_id, business_day, last_number)
            VALUES (:branchId, :day, 1)
            ON CONFLICT (branch_id, business_day)
            DO UPDATE SET last_number = blitzpay.order_pickup_counters.last_number + 1
            """.trimIndent()
        )
            .setParameter("branchId", branchId)
            .setParameter("day", day)
            .executeUpdate()
        return (entityManager.createNativeQuery(
            "SELECT last_number FROM blitzpay.order_pickup_counters WHERE branch_id = :branchId AND business_day = :day"
        )
            .setParameter("branchId", branchId)
            .setParameter("day", day)
            .singleResult as Number).toInt()
    }

    fun businessDay(branchId: UUID): LocalDate = LocalDate.now(clock.withZone(branchZone(branchId)))

    fun branchZone(branchId: UUID): ZoneId = merchantGateway.findBranchTimezone(branchId) ?: DEFAULT_BRANCH_ZONE

    companion object {
        val DEFAULT_BRANCH_ZONE: ZoneId = ZoneId.of("Europe/Berlin")
    }
}
