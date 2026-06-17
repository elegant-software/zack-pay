package com.elegant.software.blitzpay.merchant.api

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import java.util.UUID

data class MerchantContactInfo(
    val website: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
)

data class MerchantAddress(
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
)

data class MerchantLocationInfo(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geofenceRadiusMeters: Int? = null,
    val googlePlaceId: String? = null,
    val placeFormattedAddress: String? = null,
    val placeRating: Double? = null,
    val placeReviewCount: Int? = null,
    val placeEnrichmentStatus: String? = null,
    val placeEnrichedAt: java.time.Instant? = null,
)

data class MerchantOfferingResponse(
    val code: String,
    val displayName: String? = null,
    val active: Boolean = true,
)

data class MerchantOperationsResponse(
    val merchantId: UUID,
    val merchantCode: String,
    val merchantName: String,
    val merchantStatus: MerchantOperationalStatus,
    val businessType: String,
    val registrationNumber: String,
    val operatingCountry: String,
    val contactInfo: MerchantContactInfo = MerchantContactInfo(),
    val address: MerchantAddress = MerchantAddress(),
    val offerings: List<MerchantOfferingResponse> = emptyList(),
)
