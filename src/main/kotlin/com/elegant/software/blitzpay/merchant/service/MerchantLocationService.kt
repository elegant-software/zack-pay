package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MerchantLocationResponse
import com.elegant.software.blitzpay.merchant.api.NearbyBranchResponse
import com.elegant.software.blitzpay.merchant.api.NearbyMerchantResponse
import com.elegant.software.blitzpay.merchant.api.NearbyMerchantsResponse
import com.elegant.software.blitzpay.merchant.api.SetMerchantLocationRequest
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantLocation
import com.elegant.software.blitzpay.merchant.persistence.model.PostalAddress
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

@Service
class MerchantLocationService(
    private val repository: MerchantRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
) {
    private val log = LoggerFactory.getLogger(MerchantLocationService::class.java)
    private data class BranchDistance(
        val branch: com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch,
        val distanceMeters: Double?,
    )

    @Transactional
    fun setLocation(merchantId: UUID, request: SetMerchantLocationRequest): MerchantLocationResponse {
        require(request.geofenceRadiusMeters > 0) { "geofenceRadiusMeters must be positive" }
        val merchant = repository.findById(merchantId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found") }
        merchant.updateLocation(
            MerchantLocation(
                latitude = request.latitude,
                longitude = request.longitude,
                geofenceRadiusMeters = request.geofenceRadiusMeters,
                googlePlaceId = request.googlePlaceId,
                placeEnrichmentStatus = request.googlePlaceId?.let { "PENDING" }
            )
        )
        merchant.address = PostalAddress(
            addressLine1 = request.addressLine1,
            addressLine2 = request.addressLine2,
            city = request.city,
            postalCode = request.postalCode,
            country = request.country,
        )
        repository.save(merchant)
        log.info("Location set for merchant {}", merchantId)
        return merchant.location!!.toResponse(merchantId, merchant.address)
    }

    @Transactional(readOnly = true)
    fun getLocation(merchantId: UUID): MerchantLocationResponse {
        val merchant = repository.findById(merchantId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found") }
        val location = merchant.location
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No location set for merchant")
        return location.toResponse(merchantId, merchant.address)
    }

    @Transactional
    fun deleteLocation(merchantId: UUID) {
        val merchant = repository.findById(merchantId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found") }
        merchant.clearLocation()
        repository.save(merchant)
    }

    @Transactional(readOnly = true)
    fun findNearby(lat: Double, lng: Double, radiusMeters: Double): NearbyMerchantsResponse {
        require(radiusMeters > 0) { "radiusMeters must be positive" }
        val nearbyBranchesByMerchantId = merchantBranchRepository.findAllByActiveTrue()
            .asSequence()
            .mapNotNull { branch ->
                val location = branch.location ?: return@mapNotNull null
                val distanceMeters = haversineMeters(lat, lng, location.latitude, location.longitude)
                if (distanceMeters <= radiusMeters) BranchDistance(branch, distanceMeters) else null
            }
            .groupBy { it.branch.merchantId }

        if (nearbyBranchesByMerchantId.isEmpty()) {
            return NearbyMerchantsResponse(emptyList())
        }

        val merchantsById = repository.findAllById(nearbyBranchesByMerchantId.keys)
            .associateBy { it.id }

        return NearbyMerchantsResponse(
            merchants = nearbyBranchesByMerchantId.entries.mapNotNull { (merchantId, branchDistances) ->
                val merchant = merchantsById[merchantId] ?: return@mapNotNull null
                val nearestBranch = branchDistances.minByOrNull { requireNotNull(it.distanceMeters) } ?: return@mapNotNull null
                val nearestLocation = nearestBranch.branch.location ?: return@mapNotNull null

                NearbyMerchantResponse(
                    merchantId = merchant.id,
                    legalBusinessName = merchant.merchantName,
                    latitude = nearestLocation.latitude,
                    longitude = nearestLocation.longitude,
                    geofenceRadiusMeters = nearestLocation.geofenceRadiusMeters,
                    googlePlaceId = nearestLocation.googlePlaceId,
                    distanceMeters = requireNotNull(nearestBranch.distanceMeters),
                    activeBranches = branchDistances
                        .sortedBy { requireNotNull(it.distanceMeters) }
                        .map { branchDistance ->
                            val branch = branchDistance.branch
                            val branchLocation = requireNotNull(branch.location)
                            NearbyBranchResponse(
                                branchId = branch.id,
                                name = branch.name,
                                distanceMeters = branchDistance.distanceMeters,
                                latitude = branchLocation.latitude,
                                longitude = branchLocation.longitude,
                                addressLine1 = branch.address?.addressLine1,
                                city = branch.address?.city,
                                postalCode = branch.address?.postalCode,
                                country = branch.address?.country,
                                contactFullName = branch.contactFullName,
                                contactEmail = branch.contactEmail,
                                contactPhoneNumber = branch.contactPhoneNumber,
                                originalMenuLanguage = merchant.originalMenuLanguage,
                            )
                        }
                )
            }.sortedBy { it.distanceMeters }
        )
    }

    private fun MerchantLocation.toResponse(merchantId: UUID, address: PostalAddress?) = MerchantLocationResponse(
        merchantId = merchantId,
        latitude = latitude,
        longitude = longitude,
        geofenceRadiusMeters = geofenceRadiusMeters,
        googlePlaceId = googlePlaceId,
        addressLine1 = address?.addressLine1,
        addressLine2 = address?.addressLine2,
        city = address?.city,
        postalCode = address?.postalCode,
        country = address?.country,
        placeFormattedAddress = placeFormattedAddress,
        placeRating = placeRating,
        placeReviewCount = placeReviewCount,
        placeEnrichmentStatus = placeEnrichmentStatus,
        placeEnrichedAt = placeEnrichedAt
    )

    private fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        return r * acos(
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2))
                * cos(Math.toRadians(lng2) - Math.toRadians(lng1))
                + sin(Math.toRadians(lat1)) * sin(Math.toRadians(lat2))
        )
    }
}
