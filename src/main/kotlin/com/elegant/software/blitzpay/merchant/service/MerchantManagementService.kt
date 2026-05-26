package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.api.MerchantAddress
import com.elegant.software.blitzpay.merchant.api.MerchantContactInfo
import com.elegant.software.blitzpay.merchant.api.MerchantLocationInfo
import com.elegant.software.blitzpay.merchant.api.MerchantNameUpdated
import com.elegant.software.blitzpay.merchant.api.UpdateMerchantRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantLocation
import com.elegant.software.blitzpay.merchant.domain.MerchantMobility
import com.elegant.software.blitzpay.merchant.domain.PostalAddress
import com.elegant.software.blitzpay.merchant.domain.MerchantOfferingAssignment
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantOfferingRepository
import com.elegant.software.blitzpay.merchant.repository.MonitoringRecordRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MerchantManagementService(
    private val repository: MerchantApplicationRepository,
    private val branchRepository: MerchantBranchRepository,
    private val monitoringRecordRepository: MonitoringRecordRepository,
    private val merchantOfferingRepository: MerchantOfferingRepository,
    private val merchantOfferingAssignmentRepository: MerchantOfferingAssignmentRepository,
    private val storageService: StorageService,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(MerchantManagementService::class.java)

    fun get(applicationId: UUID): MerchantDetailsResponse =
        repository.findById(applicationId)
            .orElseThrow { NoSuchElementException("Merchant application not found: $applicationId") }
            .toDetailsResponse()

    fun update(applicationId: UUID, request: UpdateMerchantRequest): MerchantDetailsResponse {
        val application = repository.findById(applicationId)
            .orElseThrow { NoSuchElementException("Merchant application not found: $applicationId") }

        val resolvedMerchantName = request.merchantName
            ?: request.legalBusinessName
            ?: application.merchantName.ifBlank { application.businessProfile.legalBusinessName }
        require(resolvedMerchantName.isNotBlank()) { "merchantName must not be blank" }

        val resolvedAddress = request.address ?: MerchantAddress(
            addressLine1 = request.primaryBusinessAddress ?: application.address?.addressLine1 ?: application.businessProfile.primaryBusinessAddress,
            addressLine2 = application.address?.addressLine2,
            city = application.address?.city,
            postalCode = application.address?.postalCode,
            country = application.address?.country,
        )

        val resolvedContactInfo = request.contactInfo ?: MerchantContactInfo(
            website = request.website ?: application.website,
            email = request.contactEmail ?: application.publicEmail ?: application.primaryContact.email,
            phoneNumber = request.contactPhoneNumber ?: application.publicPhoneNumber ?: application.primaryContact.phoneNumber,
        )
        val resolvedContactFullName = request.contactFullName ?: application.primaryContact.fullName.ifBlank { resolvedMerchantName }

        val previousName = application.merchantName.ifBlank { application.businessProfile.legalBusinessName }
        application.updateProfile(
            legalBusinessName = resolvedMerchantName,
            primaryBusinessAddress = flattenAddress(resolvedAddress),
            businessType = request.businessType,
            primaryContact = PrimaryContact(
                fullName = resolvedContactFullName,
                email = resolvedContactInfo.email.orEmpty(),
                phoneNumber = resolvedContactInfo.phoneNumber.orEmpty()
            ),
            nextStatus = request.status
        )
        application.merchantStatus = request.merchantStatus ?: application.merchantStatus
        request.merchantMobility?.uppercase()?.let {
            application.merchantMobility = runCatching { MerchantMobility.valueOf(it) }
                .getOrElse { throw IllegalArgumentException("Unknown merchantMobility: $it. Valid values: ${MerchantMobility.entries.joinToString()}") }
        }
        application.website = resolvedContactInfo.website
        application.publicEmail = resolvedContactInfo.email
        application.publicPhoneNumber = resolvedContactInfo.phoneNumber
        request.location?.let {
            application.updateLocation(it.toDomainLocation())
            application.address = PostalAddress(
                addressLine1 = resolvedAddress.addressLine1,
                addressLine2 = resolvedAddress.addressLine2,
                city = resolvedAddress.city,
                postalCode = resolvedAddress.postalCode,
                country = resolvedAddress.country,
            )
        }
        request.offerings?.let { syncOfferings(application.id, it) }

        val saved = repository.save(application)
        if (saved.merchantName != previousName) {
            eventPublisher.publishEvent(MerchantNameUpdated(applicationId, saved.merchantName))
        }
        return saved.toDetailsResponse()
    }

    @Transactional
    fun deleteMerchantApplication(applicationId: UUID) {
        val application = repository.findById(applicationId)
            .orElseThrow { NoSuchElementException("Merchant application not found: $applicationId") }
        branchRepository.deleteAllByMerchantApplicationId(applicationId)
        application.monitoringRecord?.let { monitoringRecordRepository.deleteById(it.id) }
        repository.delete(application)
        log.info("merchant application deleted id={}", applicationId)
    }

    private fun MerchantApplication.toDetailsResponse() = MerchantDetailsResponse(
        applicationId = id,
        applicationReference = applicationReference,
        merchantCode = merchantCode,
        merchantName = merchantName.ifBlank { businessProfile.legalBusinessName },
        merchantStatus = merchantStatus,
        registrationNumber = businessProfile.registrationNumber,
        businessType = businessProfile.businessType,
        operatingCountry = businessProfile.operatingCountry,
        legalBusinessName = businessProfile.legalBusinessName,
        primaryBusinessAddress = businessProfile.primaryBusinessAddress,
        website = website,
        contactFullName = primaryContact.fullName,
        contactEmail = primaryContact.email,
        contactPhoneNumber = primaryContact.phoneNumber,
        status = status,
        submittedAt = submittedAt,
        lastUpdatedAt = lastUpdatedAt,
        logoStorageKey = businessProfile.logoStorageKey,
        logoUrl = signedUrl(businessProfile.logoStorageKey),
        contactInfo = MerchantContactInfo(
            website = website,
            email = publicEmail ?: primaryContact.email,
            phoneNumber = publicPhoneNumber ?: primaryContact.phoneNumber,
        ),
        address = MerchantAddress(
            addressLine1 = address?.addressLine1 ?: businessProfile.primaryBusinessAddress.takeIf { it.isNotBlank() },
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
        offerings = merchantOfferingAssignmentRepository.findAllByMerchantApplicationId(id)
            .map { it.offeringCode }
            .toSortedSet(),
        merchantMobility = merchantMobility.name,
    )

    private fun signedUrl(storageKey: String?): String? =
        storageKey?.let { runCatching { storageService.presignDownload(it) }.getOrNull() }

    private fun flattenAddress(address: MerchantAddress): String =
        listOfNotNull(
            address.addressLine1?.takeIf { it.isNotBlank() },
            address.addressLine2?.takeIf { it.isNotBlank() },
            address.postalCode?.takeIf { it.isNotBlank() },
            address.city?.takeIf { it.isNotBlank() },
            address.country?.takeIf { it.isNotBlank() },
        ).joinToString(", ")

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

    private fun syncOfferings(merchantId: UUID, offerings: Set<String>) {
        val normalized = offerings.map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
        require(
            "DEFERRED_PAYMENT" !in normalized || normalized.any { it == "PRE_ORDER" || it == "WALK_IN_ORDERING" }
        ) {
            "DEFERRED_PAYMENT requires PRE_ORDER or WALK_IN_ORDERING"
        }
        val knownCodes = merchantOfferingRepository.findAllByActiveTrueOrderByDisplayNameAsc().map { it.code }.toSet()
        val unknown = normalized - knownCodes
        require(unknown.isEmpty()) { "Unknown merchant offerings: ${unknown.joinToString(",")}" }

        val existing = merchantOfferingAssignmentRepository.findAllByMerchantApplicationId(merchantId).associateBy { it.offeringCode }
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
