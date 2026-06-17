package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class MerchantLocation(
    @Column(nullable = false)
    val latitude: Double,
    @Column(nullable = false)
    val longitude: Double,

    @Column(name = "geofence_radius_m", nullable = false)
    val geofenceRadiusMeters: Int,

    @Column(name = "google_place_id")
    val googlePlaceId: String? = null,

    @Column(name = "place_formatted_address", length = 1024)
    val placeFormattedAddress: String? = null,

    @Column(name = "place_rating")
    val placeRating: Double? = null,

    @Column(name = "place_review_count")
    val placeReviewCount: Int? = null,

    @Column(name = "place_enrichment_status", length = 32)
    val placeEnrichmentStatus: String? = null,

    @Column(name = "place_enrichment_error", length = 1024)
    val placeEnrichmentError: String? = null,

    @Column(name = "place_enriched_at")
    val placeEnrichedAt: java.time.Instant? = null
)
