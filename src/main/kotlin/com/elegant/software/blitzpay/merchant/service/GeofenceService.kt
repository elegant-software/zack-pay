package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.GeofenceRegionResponse
import com.elegant.software.blitzpay.merchant.api.GeofenceRegionsResponse
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

@Service
class GeofenceService(
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
) {
    private val log = LoggerFactory.getLogger(GeofenceService::class.java)

    @Transactional(readOnly = true)
    fun buildRegions(lat: Double?, lng: Double?): GeofenceRegionsResponse {
        val merchantRegions = merchantApplicationRepository
            .findAllByStatus(MerchantOnboardingStatus.ACTIVE)
            .filter { it.location != null }
            .map { merchant ->
                val loc = merchant.location!!
                GeofenceRegionResponse(
                    regionId = "merchant:${merchant.id}",
                    regionType = "MERCHANT",
                    sourceId = merchant.id,
                    displayName = merchant.businessProfile.legalBusinessName,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    radiusMeters = loc.geofenceRadiusMeters,
                    distanceMeters = if (lat != null && lng != null)
                        haversineMeters(lat, lng, loc.latitude, loc.longitude) else null,
                )
            }

        val branchRegions = merchantBranchRepository
            .findAllByActiveTrue()
            .filter { it.location != null }
            .map { branch ->
                val loc = branch.location!!
                GeofenceRegionResponse(
                    regionId = "branch:${branch.id}",
                    regionType = "BRANCH",
                    sourceId = branch.id,
                    displayName = branch.name,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    radiusMeters = loc.geofenceRadiusMeters,
                    distanceMeters = if (lat != null && lng != null)
                        haversineMeters(lat, lng, loc.latitude, loc.longitude) else null,
                )
            }

        val regions = (merchantRegions + branchRegions).let { all ->
            if (lat != null && lng != null) all.sortedBy { it.distanceMeters }
            else all
        }

        log.info(
            "Geofence regions built: merchantRegions={} branchRegions={} total={} sortedByDistance={}",
            merchantRegions.size,
            branchRegions.size,
            regions.size,
            lat != null && lng != null,
        )

        return GeofenceRegionsResponse(regions)
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
