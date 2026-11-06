package com.elegant.software.blitzpay.merchant.api

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@NamedInterface("MerchantGateway")
interface MerchantGateway {
    fun findOrderableProducts(productIds: Collection<UUID>): List<OrderableMerchantProduct>

    fun requireActiveBranch(merchantId: UUID, branchId: UUID): ActiveBranchInfo

    fun getEnabledOfferings(merchantId: UUID): Set<String>

    /** The Branch's configured time zone, or null when unset, invalid or the Branch is unknown. */
    fun findBranchTimezone(branchId: UUID): ZoneId?

    fun findPreparationStations(branchId: UUID): List<PreparationStation>
}

data class MerchantSummary(
    val merchantId: UUID,
    val merchantCode: String = "",
    val merchantName: String = "",
    val merchantStatus: MerchantOperationalStatus = MerchantOperationalStatus.ACTIVE,
    val lastUpdatedAt: Instant,
    val logoStorageKey: String? = null,
    val offerings: Set<String> = emptySet(),
)

data class ActiveBranchInfo(
    val branchId: UUID,
    val merchantId: UUID,
    val location: BranchLocationInfo?,
)

data class BranchLocationInfo(
    val latitude: Double,
    val longitude: Double,
    val geofenceRadiusMeters: Double,
)
