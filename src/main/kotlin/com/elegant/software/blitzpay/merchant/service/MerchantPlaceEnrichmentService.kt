package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.config.GoogleMapsProperties
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantLocation
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class MerchantPlaceEnrichmentService(
    private val properties: GoogleMapsProperties,
    private val placeClient: GoogleMapsPlaceClient,
    private val merchantRepository: MerchantRepository,
    private val merchantBranchRepository: MerchantBranchRepository
) {
    private val log = LoggerFactory.getLogger(MerchantPlaceEnrichmentService::class.java)

    @Scheduled(fixedDelayString = "\${blitzpay.google-maps.enrichment-interval-ms:300000}")
    @Transactional
    fun enrichPendingPlaces() {
        if (!properties.enabled) return
        val merchantLimit = (properties.batchSize / 2).coerceAtLeast(1)
        val branchLimit = (properties.batchSize - merchantLimit).coerceAtLeast(1)

        merchantRepository.findPlaceIdsNeedingEnrichment(merchantLimit).forEach { merchant ->
            val location = merchant.location ?: return@forEach
            val enriched = enrich(location)
            merchant.updateLocation(enriched)
            merchantRepository.save(merchant)
        }

        merchantBranchRepository.findPlaceIdsNeedingEnrichment(branchLimit).forEach { branch ->
            val location = branch.location ?: return@forEach
            branch.location = enrich(location)
            merchantBranchRepository.save(branch)
        }
    }

    fun enrich(location: MerchantLocation): MerchantLocation {
        val placeId = location.googlePlaceId ?: return location
        return runCatching {
            val details = placeClient.fetchPlaceDetails(placeId)
            location.copy(
                placeFormattedAddress = details.formattedAddress,
                placeRating = details.rating,
                placeReviewCount = details.reviewCount,
                placeEnrichmentStatus = "ENRICHED",
                placeEnrichmentError = null,
                placeEnrichedAt = Instant.now()
            )
        }.getOrElse { ex ->
            log.warn("Google Place ID enrichment failed for placeId={}", placeId, ex)
            location.copy(
                placeEnrichmentStatus = "FAILED",
                placeEnrichmentError = ex.message?.take(1024),
                placeEnrichedAt = null
            )
        }
    }
}
