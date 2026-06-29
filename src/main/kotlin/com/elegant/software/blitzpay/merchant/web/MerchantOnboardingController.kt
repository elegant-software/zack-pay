package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.api.MerchantContactInfo
import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.MerchantAddress
import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.merchant.api.MerchantLocationInfo
import com.elegant.software.blitzpay.merchant.api.MerchantLogoUploadRequest
import com.elegant.software.blitzpay.merchant.api.MerchantLogoUploadResponse
import com.elegant.software.blitzpay.merchant.api.MerchantSummary
import com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest
import com.elegant.software.blitzpay.merchant.api.UpdateMerchantRequest
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantMobility
import com.elegant.software.blitzpay.merchant.service.MerchantLogoService
import com.elegant.software.blitzpay.merchant.service.MerchantManagementService
import com.elegant.software.blitzpay.merchant.service.MerchantRegistrationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class CreateMerchantRequest(
    val merchantName: String,
    val businessType: String,
    val operatingCountry: String,
    val mobility: MerchantMobility? = null,
    val website: String? = null,
    val contactEmail: String? = null,
    val contactPhoneNumber: String? = null,
    val xHandle: String? = null,
    val instagramHandle: String? = null,
    val contactInfo: MerchantContactInfo? = null,
    val address: MerchantAddress? = null,
    val location: MerchantLocationInfo? = null,
    val offerings: Set<String>? = null,
)

@Tag(name = "Merchants", description = "Endpoints for merchant management")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants", version = "1")
class MerchantOnboardingController(
    private val gateway: MerchantGateway,
    private val merchantRegistrationService: MerchantRegistrationService,
    private val merchantManagementService: MerchantManagementService,
    private val merchantLogoService: MerchantLogoService,
) {

    @Operation(summary = "Register a new merchant (directly ACTIVE)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody request: CreateMerchantRequest): MerchantSummary {
        require(request.merchantName.isNotBlank()) { "merchantName must not be blank" }
        val merchant = merchantRegistrationService.register(
            RegisterMerchantRequest(
                merchantName = request.merchantName,
                businessType = request.businessType,
                operatingCountry = request.operatingCountry,
                mobility = request.mobility,
            )
        )

        val hasExtras = request.contactInfo != null || request.address != null ||
            request.location != null || request.offerings != null ||
            request.website != null || request.contactEmail != null ||
            request.contactPhoneNumber != null || request.xHandle != null || request.instagramHandle != null

        if (hasExtras) {
            merchantManagementService.update(
                merchant.id,
                UpdateMerchantRequest(
                    contactInfo = request.contactInfo ?: MerchantContactInfo(
                        website = request.website,
                        email = request.contactEmail,
                        phoneNumber = request.contactPhoneNumber,
                        xHandle = request.xHandle,
                        instagramHandle = request.instagramHandle,
                    ),
                    address = request.address,
                    location = request.location,
                    offerings = request.offerings,
                )
            )
        }

        return MerchantSummary(
            merchantId = merchant.id,
            merchantCode = merchant.merchantCode,
            merchantName = merchant.merchantName,
            merchantStatus = merchant.merchantStatus,
            lastUpdatedAt = merchant.lastUpdatedAt,
            logoStorageKey = merchant.logoStorageKey,
        )
    }

    @Operation(summary = "Get merchant details")
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
                "Expected key format: merchants/{merchantId}/logo.{ext}"
    )
    @PutMapping("/{id}/logo")
    fun setLogo(@PathVariable id: UUID, @RequestBody request: SetLogoRequest): MerchantSummary =
        merchantLogoService.attachLogo(id, request.storageKey)
}

data class SetLogoRequest(val storageKey: String)
