package com.elegant.software.blitzpay.merchant.api

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantMobility
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class RegisterMerchantRequest(
    val merchantName: String,
    val businessType: String,
    val operatingCountry: String,
    val mobility: MerchantMobility? = null,
)

data class MerchantBusinessProfileRequest(
    val legalBusinessName: String,
    val businessType: String,
    val operatingCountry: String,
    val merchantMobility: MerchantMobility? = null,
)

data class MerchantPrimaryContactRequest(
    val fullName: String,
    val email: String,
    val phoneNumber: String
)

data class UpdateMerchantRequest(
    val merchantName: String? = null,
    val businessType: String? = null,
    val merchantStatus: MerchantOperationalStatus? = null,
    val contactInfo: MerchantContactInfo? = null,
    val address: MerchantAddress? = null,
    val location: MerchantLocationInfo? = null,
    val offerings: Set<String>? = null,
    val website: String? = null,
    val contactEmail: String? = null,
    val contactPhoneNumber: String? = null,
    val merchantMobility: MerchantMobility? = null,
    /** Code of a published storefront theme, see `GET /v1/storefront/themes`. */
    val storefrontTheme: String? = null,
    val defaultVatPercent: BigDecimal? = null,
    val originalMenuLanguage: String? = null,
    val supportedMenuLanguages: Set<String>? = null,
)

data class MerchantDetailsResponse(
    val merchantId: UUID,
    val merchantCode: String = "",
    val merchantName: String = "",
    val merchantStatus: MerchantOperationalStatus = MerchantOperationalStatus.ACTIVE,
    val businessType: String,
    val operatingCountry: String,
    val website: String? = null,
    val lastUpdatedAt: Instant,
    val logoStorageKey: String? = null,
    val logoUrl: String? = null,
    val contactInfo: MerchantContactInfo = MerchantContactInfo(),
    val address: MerchantAddress = MerchantAddress(),
    val location: MerchantLocationInfo? = null,
    val offerings: Set<String> = emptySet(),
    val merchantMobility: MerchantMobility = MerchantMobility.BRICK_AND_MORTAR,
    val storefrontTheme: String = "DEFAULT",
    val defaultVatPercent: BigDecimal? = null,
    val originalMenuLanguage: String? = null,
    val supportedMenuLanguages: Set<String> = emptySet(),
)

data class MerchantVerticalResponse(
    val code: String,
    val displayName: String,
)

data class MerchantLogoUploadRequest(
    val contentType: String
)

data class MerchantLogoUploadResponse(
    val storageKey: String,
    val uploadUrl: String,
    val expiresAt: Instant
)
