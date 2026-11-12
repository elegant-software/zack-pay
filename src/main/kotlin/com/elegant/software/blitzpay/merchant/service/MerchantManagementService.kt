package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.MerchantAddress
import com.elegant.software.blitzpay.merchant.api.MerchantContactInfo
import com.elegant.software.blitzpay.merchant.api.MerchantLocationInfo
import com.elegant.software.blitzpay.merchant.api.MerchantNameUpdated
import com.elegant.software.blitzpay.merchant.api.UpdateMerchantRequest
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantLocation
import com.elegant.software.blitzpay.merchant.persistence.model.PostalAddress
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOfferingAssignment
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MerchantManagementService(
    private val repository: MerchantRepository,
    private val branchRepository: MerchantBranchRepository,
    private val merchantOfferingRepository: MerchantOfferingRepository,
    private val merchantOfferingAssignmentRepository: MerchantOfferingAssignmentRepository,
    private val storageService: StorageService,
    private val eventPublisher: ApplicationEventPublisher,
    private val menuLanguageService: MenuLanguageService,
    private val tableService: MerchantTableService,
    private val storefrontThemeService: StorefrontThemeService,
    private val kioskService: com.elegant.software.blitzpay.merchant.iam.service.KioskService,
) {
    private val log = LoggerFactory.getLogger(MerchantManagementService::class.java)

    fun get(merchantId: UUID): MerchantDetailsResponse =
        repository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }
            .toDetailsResponse()

    fun update(merchantId: UUID, request: UpdateMerchantRequest): MerchantDetailsResponse {
        val merchant = repository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }

        val resolvedMerchantName = request.merchantName ?: merchant.merchantName
        require(resolvedMerchantName.isNotBlank()) { "merchantName must not be blank" }

        val resolvedContactInfo = request.contactInfo ?: MerchantContactInfo(
            website = request.website ?: merchant.website,
            email = request.contactEmail ?: merchant.publicEmail,
            phoneNumber = request.contactPhoneNumber ?: merchant.publicPhoneNumber,
            xHandle = merchant.xHandle,
            instagramHandle = merchant.instagramHandle,
        )

        val previousName = merchant.merchantName
        merchant.merchantName = resolvedMerchantName
        request.businessType?.let { merchant.businessType = it }
        merchant.merchantStatus = request.merchantStatus ?: merchant.merchantStatus
        request.merchantMobility?.let { merchant.merchantMobility = it }
        request.storefrontTheme?.takeIf { it != merchant.storefrontTheme }?.let {
            storefrontThemeService.requireSelectable(it)
            merchant.storefrontTheme = it
        }
        request.defaultVatPercent?.let { merchant.defaultVatPercent = it }
        merchant.website = resolvedContactInfo.website
        merchant.publicEmail = resolvedContactInfo.email
        merchant.publicPhoneNumber = resolvedContactInfo.phoneNumber
        merchant.xHandle = resolvedContactInfo.xHandle
        merchant.instagramHandle = resolvedContactInfo.instagramHandle

        request.address?.let { addr ->
            merchant.address = PostalAddress(
                addressLine1 = addr.addressLine1,
                addressLine2 = addr.addressLine2,
                city = addr.city,
                postalCode = addr.postalCode,
                country = addr.country,
            )
        }

        request.location?.let {
            merchant.updateLocation(it.toDomainLocation())
            if (request.address != null) {
                merchant.address = PostalAddress(
                    addressLine1 = request.address.addressLine1,
                    addressLine2 = request.address.addressLine2,
                    city = request.address.city,
                    postalCode = request.address.postalCode,
                    country = request.address.country,
                )
            }
        }
        menuLanguageService.applyMenuLanguages(merchant, request.originalMenuLanguage, request.supportedMenuLanguages)
        request.offerings?.let { syncOfferings(merchant, it) }

        val saved = repository.save(merchant)
        if (saved.merchantName != previousName) {
            eventPublisher.publishEvent(MerchantNameUpdated(merchantId, saved.merchantName))
        }
        return saved.toDetailsResponse()
    }

    @Transactional
    fun deleteMerchant(merchantId: UUID) {
        val merchant = repository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }
        val branchIds = branchRepository.findAllByMerchantId(merchantId).map { it.id }
        tableService.deleteAllForBranches(branchIds)
        kioskService.deleteAllForBranches(branchIds)
        branchRepository.deleteAllByMerchantId(merchantId)
        menuLanguageService.deleteAllFor(merchantId)
        repository.delete(merchant)
        log.info("merchant deleted id={}", merchantId)
    }

    private fun Merchant.toDetailsResponse() = MerchantDetailsResponse(
        merchantId = id,
        merchantCode = merchantCode,
        merchantName = merchantName,
        merchantStatus = merchantStatus,
        businessType = businessType,
        operatingCountry = operatingCountry,
        website = website,
        lastUpdatedAt = lastUpdatedAt,
        logoStorageKey = logoStorageKey,
        logoUrl = signedUrl(logoStorageKey),
        contactInfo = MerchantContactInfo(
            website = website,
            email = publicEmail,
            phoneNumber = publicPhoneNumber,
            xHandle = xHandle,
            instagramHandle = instagramHandle,
        ),
        address = MerchantAddress(
            addressLine1 = address?.addressLine1,
            addressLine2 = address?.addressLine2,
            city = address?.city,
            postalCode = address?.postalCode,
            country = address?.country,
        ),
        location = location?.let {
            MerchantLocationInfo(
                latitude = it.latitude,
                longitude = it.longitude,
                geofenceRadiusMeters = it.geofenceRadiusMeters,
                googlePlaceId = it.googlePlaceId,
                placeFormattedAddress = it.placeFormattedAddress,
                placeRating = it.placeRating,
                placeReviewCount = it.placeReviewCount,
                placeEnrichmentStatus = it.placeEnrichmentStatus,
                placeEnrichedAt = it.placeEnrichedAt,
            )
        },
        offerings = merchantOfferingAssignmentRepository.findAllByMerchantId(id)
            .map { it.offeringCode }
            .toSortedSet(),
        merchantMobility = merchantMobility,
        storefrontTheme = storefrontTheme,
        defaultVatPercent = defaultVatPercent,
        originalMenuLanguage = originalMenuLanguage,
        supportedMenuLanguages = menuLanguageService.supportedLanguages(id),
    )

    private fun signedUrl(storageKey: String?): String? =
        storageKey?.let { runCatching { storageService.presignDownload(it) }.getOrNull() }

    private fun MerchantLocationInfo.toDomainLocation(): MerchantLocation {
        require(latitude != null) { "location.latitude must not be null" }
        require(longitude != null) { "location.longitude must not be null" }
        requireNotNull(geofenceRadiusMeters) { "location.geofenceRadiusMeters must not be null" }
        require(geofenceRadiusMeters > 0) { "location.geofenceRadiusMeters must be positive" }
        return MerchantLocation(
            latitude = latitude,
            longitude = longitude,
            geofenceRadiusMeters = geofenceRadiusMeters,
            googlePlaceId = googlePlaceId,
            placeEnrichmentStatus = googlePlaceId?.let { "PENDING" } ?: placeEnrichmentStatus,
        )
    }

    private fun syncOfferings(merchant: Merchant, offerings: Set<String>) {
        val merchantId = merchant.id
        val normalized = offerings.map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
        menuLanguageService.requireInternationalMenuReady(merchant, normalized)
        require(
            "DEFERRED_PAYMENT" !in normalized || normalized.any { it == "PRE_ORDER" || it == "WALK_IN_ORDERING" }
        ) {
            "DEFERRED_PAYMENT requires PRE_ORDER or WALK_IN_ORDERING"
        }
        val knownCodes = merchantOfferingRepository.findAllByActiveTrueOrderByDisplayNameAsc().map { it.code }.toSet()
        val unknown = normalized - knownCodes
        require(unknown.isEmpty()) { "Unknown merchant offerings: ${unknown.joinToString(",")}" }

        val existing = merchantOfferingAssignmentRepository.findAllByMerchantId(merchantId).associateBy { it.offeringCode }
        val toDelete = existing.keys - normalized
        val toCreate = normalized - existing.keys

        if (toDelete.isNotEmpty()) {
            merchantOfferingAssignmentRepository.deleteAll(existing.values.filter { it.offeringCode in toDelete })
        }
        if (toCreate.isNotEmpty()) {
            merchantOfferingAssignmentRepository.saveAll(toCreate.map { MerchantOfferingAssignment(merchantId, it) })
        }
    }
}
