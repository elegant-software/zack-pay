package com.elegant.software.blitzpay.merchant.api

import java.time.Instant
import java.util.UUID

data class CreateBranchRequest(
    val name: String,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val contactFullName: String? = null,
    val website: String? = null,
    val contactEmail: String? = null,
    val contactPhoneNumber: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geofenceRadiusMeters: Int? = null,
    val googlePlaceId: String? = null,
    /** Branch Capacity: maximum guests at the same time; optional, never blocks ordering. */
    val capacity: Int? = null,
)

data class UpdateBranchRequest(
    val name: String,
    val active: Boolean = true,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val contactFullName: String? = null,
    val website: String? = null,
    val contactEmail: String? = null,
    val contactPhoneNumber: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geofenceRadiusMeters: Int? = null,
    val googlePlaceId: String? = null,
    /** Branch Capacity; like the other fields of this full update, omitting it clears it. */
    val capacity: Int? = null,
)

data class BranchResponse(
    val id: UUID,
    val merchantId: UUID,
    val branchCode: String = "",
    val name: String,
    val active: Boolean,
    val addressLine1: String?,
    val addressLine2: String?,
    val city: String?,
    val postalCode: String?,
    val country: String?,
    val contactFullName: String?,
    val website: String? = null,
    val contactEmail: String?,
    val contactPhoneNumber: String?,
    val latitude: Double?,
    val longitude: Double?,
    val geofenceRadiusMeters: Int?,
    val googlePlaceId: String?,
    val placeFormattedAddress: String?,
    val placeRating: Double?,
    val placeReviewCount: Int?,
    val placeEnrichmentStatus: String?,
    val placeEnrichedAt: Instant?,
    val imageUrl: String?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val capacity: Int? = null,
    /** Inherited from the merchant; never configured per branch. */
    val originalMenuLanguage: String? = null,
)

data class BulkBranchInput(
    val branchName: String,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val contactFullName: String? = null,
    val contactEmail: String? = null,
    val contactPhoneNumber: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val geofenceRadiusMeters: Int? = null,
    val googlePlaceId: String? = null,
)

data class BulkBranchUpsertResult(
    val created: List<BranchResponse>,
    val updated: List<BranchResponse>,
    val skipped: List<BulkSkippedItem>,
    val failed: List<BulkFailedItem>,
)
