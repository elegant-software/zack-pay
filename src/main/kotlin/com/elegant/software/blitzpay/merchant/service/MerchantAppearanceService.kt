package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.ActiveAppearanceItem
import com.elegant.software.blitzpay.merchant.api.AppearanceHistoryPageResponse
import com.elegant.software.blitzpay.merchant.api.AppearanceHistoryStatus
import com.elegant.software.blitzpay.merchant.api.AppearanceResponse
import com.elegant.software.blitzpay.merchant.api.AppearanceStatus
import com.elegant.software.blitzpay.merchant.api.CreateAppearanceRequest
import com.elegant.software.blitzpay.merchant.api.CurrentlyHappeningResponse
import com.elegant.software.blitzpay.merchant.domain.MerchantBranchAppearance
import com.elegant.software.blitzpay.merchant.domain.MerchantMobility
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchAppearanceRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
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
        val now = Instant.now()
        val name = request.name.trim()
        require(request.endTime.isAfter(request.startTime)) { "endTime must be after startTime" }
        require(!request.startTime.isBefore(now.minus(Duration.ofMinutes(5)))) { "startTime must not be in the past" }
        require(!request.startTime.isAfter(now.plus(Duration.ofDays(30)))) { "startTime must be within 30 days" }
        require(Duration.between(request.startTime, request.endTime) <= Duration.ofDays(7)) {
            "Appearance duration must not exceed 7 days"
        }
        require(name.isNotEmpty()) { "name is required" }
        require(name.length <= 60) { "name must not exceed 60 characters" }
        require(request.declaredLatitude in -90.0..90.0) { "declaredLatitude must be between -90 and 90" }
        require(request.declaredLongitude in -180.0..180.0) { "declaredLongitude must be between -180 and 180" }

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
                name = name,
            )
        )
        return appearance.toResponse(merchantId)
    }

    @Transactional(readOnly = true)
    fun list(merchantId: UUID, branchId: UUID): List<AppearanceResponse> {
        requireBranchBelongsToMerchant(merchantId, branchId)
        return appearanceRepository.findOperational(branchId, Instant.now())
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
        require(!appearance.endTime.isBefore(Instant.now())) { "Completed appearances cannot be cancelled" }
        appearance.cancel()
        appearanceRepository.save(appearance)
    }

    @Transactional(readOnly = true)
    fun branchHistory(
        merchantId: UUID,
        branchId: UUID,
        from: Instant,
        to: Instant,
        status: AppearanceHistoryStatus?,
        page: Int,
        size: Int,
    ): AppearanceHistoryPageResponse {
        requireBranchBelongsToMerchant(merchantId, branchId)
        requireHistoryWindow(from, to, page, size)
        val now = Instant.now()
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"))
        val rows = when (status) {
            AppearanceHistoryStatus.CANCELLED ->
                appearanceRepository.findBranchCancelledHistory(branchId, from, to, pageable)
            AppearanceHistoryStatus.COMPLETED ->
                appearanceRepository.findBranchCompletedHistory(branchId, from, to, now, pageable)
            null -> appearanceRepository.findBranchHistory(branchId, from, to, now, pageable)
        }
        return rows.toHistoryResponse(merchantId)
    }

    @Transactional(readOnly = true)
    fun merchantOperational(merchantId: UUID): List<AppearanceResponse> {
        merchantRepository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }
        return appearanceRepository.findMerchantOperational(merchantId, Instant.now())
            .map { it.toResponse(merchantId) }
    }

    @Transactional(readOnly = true)
    fun merchantHistory(
        merchantId: UUID,
        branchId: UUID?,
        from: Instant,
        to: Instant,
        status: AppearanceHistoryStatus?,
        page: Int,
        size: Int,
    ): AppearanceHistoryPageResponse {
        merchantRepository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }
        branchId?.let { requireBranchBelongsToMerchant(merchantId, it) }
        requireHistoryWindow(from, to, page, size)
        val now = Instant.now()
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime"))
        val rows = when (status) {
            AppearanceHistoryStatus.CANCELLED ->
                appearanceRepository.findMerchantCancelledHistory(merchantId, branchId, from, to, pageable)
            AppearanceHistoryStatus.COMPLETED ->
                appearanceRepository.findMerchantCompletedHistory(merchantId, branchId, from, to, now, pageable)
            null -> appearanceRepository.findMerchantHistory(merchantId, branchId, from, to, now, pageable)
        }
        return rows.toHistoryResponse(merchantId)
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

    @Scheduled(cron = "\${blitzpay.appearances.history-retention-cron:0 30 2 * * *}")
    fun purgeExpiredHistory() {
        appearanceRepository.deleteHistoryBefore(Instant.now().minus(Duration.ofDays(365)))
    }

    private fun requireHistoryWindow(from: Instant, to: Instant, page: Int, size: Int) {
        require(!from.isAfter(to)) { "from must not be after to" }
        require(page >= 0) { "page must not be negative" }
        require(size in 1..100) { "size must be between 1 and 100" }
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

    private fun Page<MerchantBranchAppearance>.toHistoryResponse(merchantId: UUID) =
        AppearanceHistoryPageResponse(
            content = content.map { it.toResponse(merchantId) },
            page = number,
            size = size,
            totalElements = totalElements,
            totalPages = totalPages,
        )

    private fun MerchantBranchAppearance.toResponse(merchantId: UUID): AppearanceResponse {
        val now = Instant.now()
        val resolvedBranch = branch ?: branchRepository.findById(branchId)
            .orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        val status = when {
            cancelledAt != null -> AppearanceStatus.CANCELLED
            endTime.isBefore(now) -> AppearanceStatus.COMPLETED
            startTime.isAfter(now) -> AppearanceStatus.UPCOMING
            else -> AppearanceStatus.ACTIVE
        }
        return AppearanceResponse(
            id = id,
            branchId = branchId,
            branchName = resolvedBranch.name,
            merchantId = merchantId,
            declaredLatitude = declaredLatitude,
            declaredLongitude = declaredLongitude,
            liveLatitude = liveLatitude,
            liveLongitude = liveLongitude,
            effectiveLatitude = effectiveLatitude,
            effectiveLongitude = effectiveLongitude,
            startTime = startTime,
            endTime = endTime,
            name = requireNotNull(name),
            createdAt = createdAt,
            cancelledAt = cancelledAt,
            updatedAt = updatedAt,
            status = status,
        )
    }

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
