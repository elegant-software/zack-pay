package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.BranchContext
import com.elegant.software.blitzpay.merchant.api.MerchantContext
import com.elegant.software.blitzpay.merchant.api.ProximityEventRequest
import com.elegant.software.blitzpay.merchant.api.ProximityResponse
import com.elegant.software.blitzpay.merchant.config.GeofenceProperties
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.persistence.model.ProximityEvent
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProviderAccountRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.ProximityEventRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

@Service
class ProximityService(
    private val proximityEventRepository: ProximityEventRepository,
    private val merchantRepository: MerchantRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
    private val merchantOfferingAssignmentRepository: MerchantOfferingAssignmentRepository,
    private val providerAccountRepository: MerchantProviderAccountRepository,
    private val storageService: StorageService,
    private val geofenceProperties: GeofenceProperties,
) {
    private val log = LoggerFactory.getLogger(ProximityService::class.java)

    private data class ResolvedRegion(
        val regionId: String,
        val regionType: String,
        val sourceId: UUID,
    )

    @Transactional
    fun record(request: ProximityEventRequest, userSubject: String?): ProximityResponse {
        val eventType = request.event.uppercase()

        val resolvedRegion = resolveRegion(request)
            ?: run {
                log.info(
                    "Proximity event ignored: no matching region for regionId={} latitude={} longitude={}",
                    request.regionId,
                    request.location.latitude,
                    request.location.longitude,
                )
                return ProximityResponse(recorded = false, action = "none")
            }

        val regionId = resolvedRegion.regionId
        val regionType = resolvedRegion.regionType
        val sourceId = resolvedRegion.sourceId

        val since = Instant.now().minusSeconds(geofenceProperties.proximityCooldownSeconds)
        val isDuplicate = isDuplicate(regionId, eventType, userSubject, request.deviceId, since)
        if (isDuplicate) {
            log.info("Proximity event deduplicated: regionId={} event={} deviceId={} authenticated={}", regionId, eventType, request.deviceId, userSubject != null)
            return ProximityResponse(recorded = false, action = "none")
        }

        proximityEventRepository.save(
            ProximityEvent(
                regionId = regionId,
                regionType = regionType,
                sourceId = sourceId,
                userSubject = userSubject,
                deviceId = request.deviceId,
                eventType = eventType,
                reportedLatitude = request.location.latitude,
                reportedLongitude = request.location.longitude,
            )
        )

        if (eventType != "ENTER") {
            log.info("Proximity event recorded without merchant context: regionId={} event={}", regionId, eventType)
            return ProximityResponse(recorded = true, action = "none")
        }

        val merchant = resolveMerchant(regionType, sourceId)
            ?: run {
                log.info("Proximity event recorded but merchant context missing: regionId={} regionType={} sourceId={}", regionId, regionType, sourceId)
                return ProximityResponse(recorded = true, action = "none")
            }

        val branches = merchantBranchRepository
            .findAllByMerchantIdAndActiveTrue(merchant.id)
            .map { branch ->
                BranchContext(
                    branchId = branch.id,
                    name = branch.name,
                    distanceMeters = branch.location?.let {
                        haversineMeters(request.location.latitude, request.location.longitude, it.latitude, it.longitude)
                    },
                    addressLine1 = branch.address?.addressLine1,
                    addressLine2 = branch.address?.addressLine2,
                    city = branch.address?.city,
                    postalCode = branch.address?.postalCode,
                    country = branch.address?.country,
                    contactFullName = branch.contactFullName,
                    contactEmail = branch.contactEmail,
                    contactPhoneNumber = branch.contactPhoneNumber,
                    originalMenuLanguage = merchant.originalMenuLanguage,
                )
            }
            .sortedWith(compareBy(nullsLast()) { it.distanceMeters })

        val logoUrl = merchant.logoStorageKey?.let {
            runCatching { storageService.presignDownload(it) }.getOrNull()
        }

        val enabledOfferingCodes = merchantOfferingAssignmentRepository
            .findAllByMerchantId(merchant.id)
            .map { it.offeringCode }
            .toSet()
        val deferredPaymentAvailable = "DEFERRED_PAYMENT" in enabledOfferingCodes

        val activePspCodes = providerAccountRepository
            .findAllByMerchantId(merchant.id)
            .filter { it.enabled }
            .map { it.pspCode }
            .toSet()
        val action = if (activePspCodes.isNotEmpty()) "notify" else "none"

        log.info("Proximity event recorded: regionId={} event={} merchant={}", regionId, eventType, merchant.id)

        return ProximityResponse(
            recorded = true,
            action = action,
            merchant = MerchantContext(
                merchantId = merchant.id,
                name = merchant.merchantName,
                logoUrl = logoUrl,
                activePspCodes = activePspCodes,
                deferredPaymentAvailable = deferredPaymentAvailable,
                branches = branches,
            ),
        )
    }

    private fun resolveRegion(request: ProximityEventRequest): ResolvedRegion? {
        val providedRegionId = request.regionId?.takeIf { it.isNotBlank() }
        val parsedRegion = providedRegionId?.let { parseRegionId(it) }
        if (parsedRegion != null) {
            return ResolvedRegion(
                regionId = providedRegionId,
                regionType = parsedRegion.first,
                sourceId = parsedRegion.second,
            )
        }

        if (providedRegionId != null) {
            log.info(
                "Proximity event regionId not recognized, falling back to location lookup: regionId={} latitude={} longitude={}",
                providedRegionId,
                request.location.latitude,
                request.location.longitude,
            )
        }

        return resolveRegionByLocation(request.location.latitude, request.location.longitude)
    }

    private fun parseRegionId(regionId: String): Pair<String, UUID>? {
        return when {
            regionId.startsWith("merchant:") -> {
                val uuid = runCatching { UUID.fromString(regionId.removePrefix("merchant:")) }.getOrNull()
                    ?: return null
                "MERCHANT" to uuid
            }
            regionId.startsWith("branch:") -> {
                val uuid = runCatching { UUID.fromString(regionId.removePrefix("branch:")) }.getOrNull()
                    ?: return null
                "BRANCH" to uuid
            }
            else -> null
        }
    }

    private fun resolveRegionByLocation(latitude: Double, longitude: Double): ResolvedRegion? {
        val matchingBranch = merchantBranchRepository.findAllByActiveTrue()
            .asSequence()
            .filter { it.location != null }
            .map { branch ->
                val location = branch.location!!
                branch to haversineMeters(latitude, longitude, location.latitude, location.longitude)
            }
            .filter { (branch, distanceMeters) -> distanceMeters <= requireNotNull(branch.location).geofenceRadiusMeters }
            .minByOrNull { it.second }
            ?.first

        if (matchingBranch != null) {
            return ResolvedRegion(
                regionId = "branch:${matchingBranch.id}",
                regionType = "BRANCH",
                sourceId = matchingBranch.id,
            )
        }

        val matchingMerchant = merchantRepository.findAllByMerchantStatus(MerchantOperationalStatus.ACTIVE)
            .asSequence()
            .filter { it.location != null }
            .map { merchant ->
                val location = merchant.location!!
                merchant to haversineMeters(latitude, longitude, location.latitude, location.longitude)
            }
            .filter { (merchant, distanceMeters) -> distanceMeters <= requireNotNull(merchant.location).geofenceRadiusMeters }
            .minByOrNull { it.second }
            ?.first

        if (matchingMerchant != null) {
            return ResolvedRegion(
                regionId = "merchant:${matchingMerchant.id}",
                regionType = "MERCHANT",
                sourceId = matchingMerchant.id,
            )
        }

        return null
    }

    private fun isDuplicate(
        regionId: String,
        eventType: String,
        userSubject: String?,
        deviceId: String?,
        since: Instant,
    ): Boolean {
        if (userSubject != null) {
            return proximityEventRepository
                .existsByRegionIdAndEventTypeAndUserSubjectAndReceivedAtAfter(regionId, eventType, userSubject, since)
        }
        if (deviceId != null) {
            return proximityEventRepository
                .existsByRegionIdAndEventTypeAndDeviceIdAndReceivedAtAfter(regionId, eventType, deviceId, since)
        }
        return false
    }

    private fun resolveMerchant(regionType: String, sourceId: UUID): Merchant? {
        return when (regionType) {
            "MERCHANT" -> merchantRepository.findById(sourceId).orElse(null)
            "BRANCH" -> {
                val branch = merchantBranchRepository.findById(sourceId).orElse(null) ?: return null
                merchantRepository.findById(branch.merchantId).orElse(null)
            }
            else -> null
        }
    }

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        return r * acos(
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2))
                * cos(Math.toRadians(lng2) - Math.toRadians(lng1))
                + sin(Math.toRadians(lat1)) * sin(Math.toRadians(lat2))
        )
    }
}
