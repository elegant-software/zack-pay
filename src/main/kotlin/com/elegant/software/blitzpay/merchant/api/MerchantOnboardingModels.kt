package com.elegant.software.blitzpay.merchant.api

import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.domain.MerchantPaymentChannel
import com.elegant.software.blitzpay.merchant.domain.PersonRole
import com.elegant.software.blitzpay.merchant.domain.SupportingMaterialType
import java.time.Instant
import java.util.UUID

data class RegisterMerchantRequest(
    val businessProfile: MerchantBusinessProfileRequest,
    val primaryContact: MerchantPrimaryContactRequest
)

data class CreateMerchantApplicationRequest(
    val businessProfile: MerchantBusinessProfileRequest,
    val primaryContact: MerchantPrimaryContactRequest
)

data class SaveMerchantApplicationRequest(
    val businessProfile: MerchantBusinessProfileRequest,
    val primaryContact: MerchantPrimaryContactRequest,
    val people: List<MerchantPersonRequest> = emptyList(),
    val supportingMaterials: List<MerchantSupportingMaterialRequest> = emptyList()
)

data class SubmitMerchantApplicationRequest(
    val submittedAt: Instant? = null
)

data class MerchantBusinessProfileRequest(
    val legalBusinessName: String,
    val businessType: String,
    val registrationNumber: String,
    val operatingCountry: String,
    val primaryBusinessAddress: String,
    val merchantMobility: String? = null,
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
    val legalBusinessName: String? = null,
    val primaryBusinessAddress: String? = null,
    val website: String? = null,
    val contactFullName: String? = null,
    val contactEmail: String? = null,
    val contactPhoneNumber: String? = null,
    val activePaymentChannels: Set<MerchantPaymentChannel>? = null,
    val status: MerchantOnboardingStatus? = null,
    val merchantMobility: String? = null,
)

data class MerchantPersonRequest(
    val fullName: String,
    val role: PersonRole,
    val countryOfResidence: String,
    val ownershipPercentage: Int = 0
)

data class MerchantSupportingMaterialRequest(
    val type: SupportingMaterialType,
    val fileName: String,
    val storageKey: String
)

data class MerchantApplicationResponse(
    val applicationId: UUID,
    val applicationReference: String,
    val status: MerchantOnboardingStatus,
    val businessProfile: MerchantBusinessProfileResponse,
    val primaryContact: MerchantPrimaryContactResponse,
    val people: List<MerchantPersonResponse>,
    val supportingMaterials: List<MerchantSupportingMaterialResponse>,
    val submittedAt: Instant?,
    val lastUpdatedAt: Instant
)

data class MerchantApplicationStatusResponse(
    val applicationId: UUID,
    val applicationReference: String,
    val status: MerchantOnboardingStatus,
    val submittedAt: Instant?,
    val lastUpdatedAt: Instant,
    val outstandingActions: List<String> = emptyList()
)

data class MerchantBusinessProfileResponse(
    val legalBusinessName: String,
    val businessType: String,
    val registrationNumber: String,
    val operatingCountry: String,
    val primaryBusinessAddress: String
)

data class MerchantPrimaryContactResponse(
    val fullName: String,
    val email: String,
    val phoneNumber: String
)

data class MerchantDetailsResponse(
    val applicationId: UUID,
    val applicationReference: String,
    val merchantCode: String = "",
    val merchantName: String = "",
    val merchantStatus: MerchantOperationalStatus = MerchantOperationalStatus.ACTIVE,
    val registrationNumber: String,
    val businessType: String,
    val operatingCountry: String,
    val legalBusinessName: String,
    val primaryBusinessAddress: String,
    val website: String? = null,
    val contactFullName: String,
    val contactEmail: String,
    val contactPhoneNumber: String,
    val activePaymentChannels: Set<MerchantPaymentChannel>,
    val status: MerchantOnboardingStatus,
    val submittedAt: Instant?,
    val lastUpdatedAt: Instant,
    val logoStorageKey: String? = null,
    val logoUrl: String? = null,
    val contactInfo: MerchantContactInfo = MerchantContactInfo(),
    val address: MerchantAddress = MerchantAddress(),
    val location: MerchantLocationInfo? = null,
    val offerings: Set<String> = emptySet(),
    val merchantMobility: String = "BRICK_AND_MORTAR",
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

data class MerchantPersonResponse(
    val fullName: String,
    val role: PersonRole,
    val countryOfResidence: String,
    val ownershipPercentage: Int
)

data class MerchantSupportingMaterialResponse(
    val type: SupportingMaterialType,
    val fileName: String,
    val uploadedAt: Instant
)
