package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.config.GoogleMapsProperties
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantLocation
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MerchantPlaceEnrichmentServiceTest {
    private val properties = GoogleMapsProperties(enabled = true)
    private val merchantRepository = mock<MerchantApplicationRepository>()
    private val branchRepository = mock<MerchantBranchRepository>()

    @Test
    fun `enrich stores Google place details on success`() {
        val service = MerchantPlaceEnrichmentService(
            properties = properties,
            placeClient = object : GoogleMapsPlaceClient(properties, mock()) {
                override fun fetchPlaceDetails(placeId: String): PlaceDetails =
                    PlaceDetails(
                        formattedAddress = "Marktplatz 1, Bremen",
                        rating = 4.7,
                        reviewCount = 42
                    )
            },
            merchantApplicationRepository = merchantRepository,
            merchantBranchRepository = branchRepository
        )

        val enriched = service.enrich(location())

        assertEquals("ENRICHED", enriched.placeEnrichmentStatus)
        assertEquals("Marktplatz 1, Bremen", enriched.placeFormattedAddress)
        assertEquals(4.7, enriched.placeRating)
        assertEquals(42, enriched.placeReviewCount)
        assertNotNull(enriched.placeEnrichedAt)
    }

    @Test
    fun `enrich records retryable failure when Google Maps fails`() {
        val service = MerchantPlaceEnrichmentService(
            properties = properties,
            placeClient = object : GoogleMapsPlaceClient(properties, mock()) {
                override fun fetchPlaceDetails(placeId: String): PlaceDetails {
                    error("upstream unavailable")
                }
            },
            merchantApplicationRepository = merchantRepository,
            merchantBranchRepository = branchRepository
        )

        val enriched = service.enrich(location())

        assertEquals("FAILED", enriched.placeEnrichmentStatus)
        assertEquals("upstream unavailable", enriched.placeEnrichmentError)
    }

    private fun location() = MerchantLocation(
        latitude = 53.0758,
        longitude = 8.8072,
        geofenceRadiusMeters = 250,
        googlePlaceId = "ChIJ-test"
    )
}
