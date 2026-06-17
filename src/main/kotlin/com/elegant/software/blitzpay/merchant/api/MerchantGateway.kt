package com.elegant.software.blitzpay.merchant.api

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.persistence.model.ReviewOutcome
import org.springframework.modulith.NamedInterface
import java.time.Instant
import java.util.UUID

@NamedInterface("MerchantGateway")
interface MerchantGateway {
    fun submit(applicationId: UUID, submittedAt: Instant = Instant.now()): MerchantSummary

    fun transition(
        applicationId: UUID,
        nextStatus: MerchantOnboardingStatus,
        changedAt: Instant = Instant.now()
    ): MerchantSummary

    fun requestChanges(
        applicationId: UUID,
        reason: String,
        changedAt: Instant = Instant.now()
    ): MerchantSummary

    fun recordDecision(
        applicationId: UUID,
        outcome: ReviewOutcome,
        reason: String,
        reviewerId: String,
        decidedAt: Instant = Instant.now()
    ): MerchantSummary

    fun startMonitoring(
        applicationId: UUID,
        triggerReason: String,
        changedAt: Instant = Instant.now()
    ): MerchantSummary

    fun findOrderableProducts(productIds: Collection<UUID>): List<OrderableMerchantProduct>

    fun requireActiveBranch(merchantId: UUID, branchId: UUID): ActiveBranchInfo

    fun getEnabledOfferings(merchantId: UUID): Set<String>
}

data class MerchantSummary(
    val applicationId: UUID,
    val applicationReference: String,
    val merchantCode: String = "",
    val merchantName: String = "",
    val merchantStatus: MerchantOperationalStatus = MerchantOperationalStatus.ACTIVE,
    val registrationNumber: String,
    val status: MerchantOnboardingStatus,
    val submittedAt: Instant?,
    val lastUpdatedAt: Instant,
    val logoStorageKey: String? = null,
    val offerings: Set<String> = emptySet(),
)

data class ActiveBranchInfo(
    val branchId: UUID,
    val merchantApplicationId: UUID,
    val location: BranchLocationInfo?,
)

data class BranchLocationInfo(
    val latitude: Double,
    val longitude: Double,
    val geofenceRadiusMeters: Double,
)
