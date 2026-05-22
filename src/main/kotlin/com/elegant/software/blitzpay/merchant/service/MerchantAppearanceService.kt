package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.ActiveAppearanceItem
import com.elegant.software.blitzpay.merchant.api.AppearanceResponse
import com.elegant.software.blitzpay.merchant.api.CreateAppearanceRequest
import com.elegant.software.blitzpay.merchant.api.CurrentlyHappeningResponse
import com.elegant.software.blitzpay.merchant.api.PushLivePositionRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantBranchAppearance
import com.elegant.software.blitzpay.merchant.domain.MerchantMobility
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchAppearanceRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Service
@Transactional
class MerchantAppearanceService(
    private val appearanceRepository: MerchantBranchAppearanceRepository,
    private val branchRepository: MerchantBranchRepository,
    private val merchantRepository: MerchantApplicationRepository,
) {

    fun create(merchantId: UUID, branchId: UUID, request: CreateAppearanceRequest): AppearanceResponse {
        require(request.endTime.isAfter(request.startTime)) { "endTime must be after startTime" }

        val merchant = merchantRepository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }
        require(merchant.merchantMobility == MerchantMobility.MOBILE) {
            "Appearances can only be created for MOBILE merchants"
        }

        val branch = branchRepository.findById(branchId)
            .orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        require(branch.merchantApplicationId == merchantId) { "Branch does not belong to merchant" }

        val overlapping = appearanceRepository.findOverlapping(branchId, request.startTime, request.endTime)
        require(overlapping.isEmpty()) {
            "Branch already has an appearance during this time window"
        }

        val appearance = appearanceRepository.save(
            MerchantBranchAppearance(
                branchId = branchId,
                declaredLatitude = request.declaredLatitude,
                declaredLongitude = request.declaredLongitude,
                startTime = request.startTime,
                endTime = request.endTime,
                name = request.name?.trim()?.takeIf { it.isNotEmpty() },
            )
        )
        return appearance.toResponse(merchantId)
    }

    @Transactional(readOnly = true)
    fun list(merchantId: UUID, branchId: UUID): List<AppearanceResponse> {
        requireBranchBelongsToMerchant(merchantId, branchId)
        return appearanceRepository.findAllByBranchId(branchId)
            .map { it.toResponse(merchantId) }
    }

    @Transactional(readOnly = true)
    fun get(merchantId: UUID, branchId: UUID, appearanceId: UUID): AppearanceResponse {
        requireBranchBelongsToMerchant(merchantId, branchId)
        return requireAppearance(appearanceId, branchId).toResponse(merchantId)
    }

    fun delete(merchantId: UUID, branchId: UUID, appearanceId: UUID) {
        requireBranchBelongsToMerchant(merchantId, branchId)
        val appearance = requireAppearance(appearanceId, branchId)
        appearanceRepository.delete(appearance)
    }

    fun pushLivePosition(
        merchantId: UUID,
        branchId: UUID,
        appearanceId: UUID,
        request: PushLivePositionRequest,
    ): AppearanceResponse {
        requireBranchBelongsToMerchant(merchantId, branchId)
        val appearance = requireAppearance(appearanceId, branchId)
        appearance.pushLivePosition(request.latitude, request.longitude)
        return appearanceRepository.save(appearance).toResponse(merchantId)
    }

    @Transactional(readOnly = true)
    fun currentlyHappening(
        latitude: Double?,
        longitude: Double?,
        radiusMeters: Double?,
    ): CurrentlyHappeningResponse {
        require((latitude == null) == (longitude == null)) {
            "latitude and longitude must both be provided or both be omitted"
        }
        if (radiusMeters != null) require(radiusMeters > 0) { "radiusMeters must be positive" }

        val now = Instant.now()
        val active = appearanceRepository.findAllActive(now)

        val items = active.mapNotNull { appearance ->
            val branch = appearance.branch
                ?: branchRepository.findById(appearance.branchId).orElse(null)
                ?: return@mapNotNull null
            val merchant = merchantRepository.findById(branch.merchantApplicationId).orElse(null)
                ?: return@mapNotNull null

            val effectiveLat = appearance.effectiveLatitude
            val effectiveLng = appearance.effectiveLongitude

            val distance = if (latitude != null && longitude != null) {
                haversineMeters(latitude, longitude, effectiveLat, effectiveLng)
            } else null

            if (distance != null && radiusMeters != null && distance > radiusMeters) return@mapNotNull null

            ActiveAppearanceItem(
                appearanceId = appearance.id,
                merchantId = merchant.id,
                merchantName = merchant.merchantName.ifBlank { merchant.businessProfile.legalBusinessName },
                branchId = branch.id,
                branchName = branch.name,
                name = appearance.name,
                effectiveLatitude = effectiveLat,
                effectiveLongitude = effectiveLng,
                distanceMeters = distance,
                startTime = appearance.startTime,
                endTime = appearance.endTime,
            )
        }.let { list ->
            if (latitude != null) list.sortedBy { it.distanceMeters } else list
        }

        return CurrentlyHappeningResponse(appearances = items)
    }

    private fun requireBranchBelongsToMerchant(merchantId: UUID, branchId: UUID) {
        val branch = branchRepository.findById(branchId)
            .orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        require(branch.merchantApplicationId == merchantId) { "Branch does not belong to merchant" }
    }

    private fun requireAppearance(appearanceId: UUID, branchId: UUID): MerchantBranchAppearance {
        val appearance = appearanceRepository.findById(appearanceId)
            .orElseThrow { NoSuchElementException("Appearance not found: $appearanceId") }
        require(appearance.branchId == branchId) { "Appearance does not belong to branch" }
        return appearance
    }

    private fun MerchantBranchAppearance.toResponse(merchantId: UUID) = AppearanceResponse(
        id = id,
        branchId = branchId,
        merchantId = merchantId,
        declaredLatitude = declaredLatitude,
        declaredLongitude = declaredLongitude,
        liveLatitude = liveLatitude,
        liveLongitude = liveLongitude,
        effectiveLatitude = effectiveLatitude,
        effectiveLongitude = effectiveLongitude,
        startTime = startTime,
        endTime = endTime,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
            return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}
