package com.elegant.software.blitzpay.merchant.api

import java.util.UUID

data class ProximityLocation(
    val latitude: Double,
    val longitude: Double,
)

data class ProximityEventRequest(
    val regionId: String? = null,
    val event: String,
    val location: ProximityLocation,
    val timestamp: String,
    val deviceId: String? = null,
)

data class BranchContext(
    val branchId: UUID,
    val name: String,
    val distanceMeters: Double? = null,
    val addressLine1: String? = null,
    val addressLine2: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    val contactFullName: String? = null,
    val contactEmail: String? = null,
    val contactPhoneNumber: String? = null,
    val activePspCodes: Set<String> = emptySet(),
)

data class MerchantContext(
    val merchantId: UUID,
    val name: String,
    val logoUrl: String? = null,
    val activePspCodes: Set<String> = emptySet(),
    val deferredPaymentAvailable: Boolean = false,
    val branches: List<BranchContext> = emptyList(),
)

data class ProximityResponse(
    val recorded: Boolean,
    val action: String,
    val merchant: MerchantContext? = null,
)
