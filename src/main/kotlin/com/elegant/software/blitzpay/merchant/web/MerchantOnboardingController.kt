package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.MerchantBusinessProfileRequest
import com.elegant.software.blitzpay.merchant.api.MerchantContactInfo
import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.MerchantAddress
import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.merchant.api.MerchantLocationInfo
import com.elegant.software.blitzpay.merchant.api.MerchantLogoUploadRequest
import com.elegant.software.blitzpay.merchant.api.MerchantLogoUploadResponse
import com.elegant.software.blitzpay.merchant.api.MerchantPrimaryContactRequest
import com.elegant.software.blitzpay.merchant.api.MerchantSummary
import com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest
import com.elegant.software.blitzpay.merchant.api.UpdateMerchantRequest
import com.elegant.software.blitzpay.merchant.application.MerchantLogoService
import com.elegant.software.blitzpay.merchant.application.MerchantManagementService
import com.elegant.software.blitzpay.merchant.application.MerchantRegistrationService
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class CreateMerchantRequest(
    val merchantName: String? = null,
    val contactInfo: MerchantContactInfo? = null,
    val address: MerchantAddress? = null,
    val location: MerchantLocationInfo? = null,
    val offerings: Set<String>? = null,
    val legalBusinessName: String? = null,
    val businessType: String,
    val registrationNumber: String,
    val operatingCountry: String,
    val primaryBusinessAddress: String? = null,
    val contactFullName: String? = null,
    val contactEmail: String? = null,
    val contactPhoneNumber: String? = null
)

@Tag(name = "Merchant Onboarding", description = "Endpoints for merchant onboarding and lifecycle")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants", version = "1")
class MerchantOnboardingController(
    private val repository: MerchantApplicationRepository,
    private val gateway: MerchantGateway,
    private val merchantRegistrationService: MerchantRegistrationService,
    private val merchantManagementService: MerchantManagementService,
    private val merchantLogoService: MerchantLogoService,
) {

    @Operation(summary = "Register a new merchant (directly ACTIVE, duplicate registration number rejected with 409)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateMerchantRequest): MerchantSummary {
        val merchantName = request.merchantName ?: request.legalBusinessName
        require(!merchantName.isNullOrBlank()) { "merchantName must not be blank" }
        val application = merchantRegistrationService.register(
            RegisterMerchantRequest(
                businessProfile = MerchantBusinessProfileRequest(
                    legalBusinessName = merchantName,
                    businessType = request.businessType,
                    registrationNumber = request.registrationNumber,
                    operatingCountry = request.operatingCountry,
                    primaryBusinessAddress = request.primaryBusinessAddress
                        ?: listOfNotNull(
                            request.address?.addressLine1,
                            request.address?.addressLine2,
                            request.address?.postalCode,
                            request.address?.city,
                            request.address?.country,
                        ).joinToString(", ")
                ),
                primaryContact = MerchantPrimaryContactRequest(
                    fullName = request.contactFullName ?: merchantName,
                    email = request.contactInfo?.email ?: request.contactEmail ?: "${applicationEmailSlug(merchantName)}@merchant.local",
                    phoneNumber = request.contactInfo?.phoneNumber ?: request.contactPhoneNumber ?: "0000000000"
                )
            )
        )
        val enriched = if (
            request.contactInfo?.website != null || request.address != null || request.location != null || request.offerings != null
        ) {
            merchantManagementService.update(
                application.id,
                UpdateMerchantRequest(
                    merchantName = merchantName,
                    contactInfo = request.contactInfo,
                    address = request.address,
                    location = request.location,
                    offerings = request.offerings,
                )
            )
        } else {
            MerchantDetailsResponse(
                applicationId = application.id,
                applicationReference = application.applicationReference,
                merchantCode = application.merchantCode,
                merchantName = application.merchantName.ifBlank { application.businessProfile.legalBusinessName },
                merchantStatus = application.merchantStatus,
                registrationNumber = application.businessProfile.registrationNumber,
                businessType = application.businessProfile.businessType,
                operatingCountry = application.businessProfile.operatingCountry,
                legalBusinessName = application.businessProfile.legalBusinessName,
                primaryBusinessAddress = application.businessProfile.primaryBusinessAddress,
                website = application.website,
                contactFullName = application.primaryContact.fullName,
                contactEmail = application.publicEmail ?: application.primaryContact.email,
                contactPhoneNumber = application.publicPhoneNumber ?: application.primaryContact.phoneNumber,
                status = application.status,
                submittedAt = application.submittedAt,
                lastUpdatedAt = application.lastUpdatedAt,
                logoStorageKey = application.businessProfile.logoStorageKey,
                contactInfo = MerchantContactInfo(
                    website = application.website,
                    email = application.publicEmail ?: application.primaryContact.email,
                    phoneNumber = application.publicPhoneNumber ?: application.primaryContact.phoneNumber,
                ),
                address = MerchantAddress(
                    addressLine1 = application.businessProfile.primaryBusinessAddress.takeIf { it.isNotBlank() },
                ),
                offerings = emptySet(),
            )
        }
        return MerchantSummary(
            applicationId = application.id,
            applicationReference = application.applicationReference,
            merchantCode = enriched.merchantCode,
            merchantName = enriched.merchantName,
            merchantStatus = enriched.merchantStatus,
            registrationNumber = enriched.registrationNumber,
            status = application.status,
            submittedAt = application.submittedAt,
            lastUpdatedAt = application.lastUpdatedAt,
            offerings = enriched.offerings,
        )
    }

    @Operation(summary = "Directly activate a merchant application (skips onboarding flow)")
    @PostMapping("/{id}/activate")
    fun activate(@PathVariable id: UUID): MerchantSummary {
        val application = repository.findById(id)
            .orElseThrow { NoSuchElementException("Merchant application not found: $id") }
        
        application.registerDirect()
        repository.save(application)
        
        return MerchantSummary(
            applicationId = application.id,
            applicationReference = application.applicationReference,
            merchantCode = application.merchantCode,
            merchantName = application.merchantName.ifBlank { application.businessProfile.legalBusinessName },
            merchantStatus = application.merchantStatus,
            registrationNumber = application.businessProfile.registrationNumber,
            status = application.status,
            submittedAt = application.submittedAt,
            lastUpdatedAt = application.lastUpdatedAt
        )
    }

    @Operation(summary = "Get merchant application details")
    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): MerchantDetailsResponse = merchantManagementService.get(id)

    @Operation(summary = "Update editable merchant details and optional status")
    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @RequestBody request: UpdateMerchantRequest): MerchantDetailsResponse =
        merchantManagementService.update(id, request)

    @Operation(
        summary = "Create a merchant logo upload URL",
        description = "Generates a presigned upload URL for a merchant logo. Supported content types: image/jpeg, image/png, image/webp."
    )
    @PostMapping("/{id}/logo/upload-url")
    fun createLogoUploadUrl(
        @PathVariable id: UUID,
        @RequestBody request: MerchantLogoUploadRequest
    ): MerchantLogoUploadResponse = merchantLogoService.createUploadUrl(id, request.contentType)

    @Operation(
        summary = "Set merchant logo",
        description = "Records the S3 storage key of a logo already uploaded by the client. " +
                "Expected key format: merchants/{applicationId}/logo.{ext}"
    )
    @PutMapping("/{id}/logo")
    fun setLogo(@PathVariable id: UUID, @RequestBody request: SetLogoRequest): MerchantSummary =
        merchantLogoService.attachLogo(id, request.storageKey)
}

data class SetLogoRequest(val storageKey: String)

private fun applicationEmailSlug(merchantName: String): String =
    merchantName.lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.').ifBlank { "merchant" }
