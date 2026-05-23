package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.BranchCreated
import com.elegant.software.blitzpay.merchant.api.BranchNameUpdated
import com.elegant.software.blitzpay.merchant.api.BranchResponse
import com.elegant.software.blitzpay.merchant.api.CreateBranchRequest
import com.elegant.software.blitzpay.merchant.api.UpdateBranchRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantBranch
import com.elegant.software.blitzpay.merchant.domain.MerchantEntityStatus
import com.elegant.software.blitzpay.merchant.domain.MerchantLocation
import com.elegant.software.blitzpay.merchant.domain.PostalAddress
import com.elegant.software.blitzpay.merchant.domain.MerchantPaymentChannel
import com.elegant.software.blitzpay.merchant.config.GeofenceProperties
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class MerchantBranchService(
    private val merchantBranchRepository: MerchantBranchRepository,
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val storageService: StorageService,
    private val eventPublisher: ApplicationEventPublisher,
    private val geofenceProperties: GeofenceProperties,
) {

    fun create(merchantId: UUID, request: CreateBranchRequest, active: Boolean = true): BranchResponse {
        require(request.name.isNotBlank()) { "Branch name must not be blank" }
        require((request.latitude == null) == (request.longitude == null)) {
            "latitude and longitude must both be provided or both be omitted"
        }
        request.geofenceRadiusMeters?.let { require(it > 0) { "geofenceRadiusMeters must be positive" } }
        if (active) {
            require(request.addressLine1?.isNotBlank() == true) { "addressLine1 must not be blank for active branches" }
            require(request.city?.isNotBlank() == true) { "city must not be blank for active branches" }
            require(request.postalCode?.isNotBlank() == true) { "postalCode must not be blank for active branches" }
            require(request.country?.isNotBlank() == true) { "country must not be blank for active branches" }
            require(request.latitude != null && request.longitude != null) { "latitude and longitude are required for active branches" }
            require(request.geofenceRadiusMeters != null) { "geofenceRadiusMeters is required for active branches" }
        }
        val merchant = requireMerchant(merchantId)
        val branch = MerchantBranch(
            merchantApplicationId = merchantId,
            name = request.name,
            active = active,
            status = if (active) MerchantEntityStatus.ACTIVE else MerchantEntityStatus.INACTIVE,
            address = PostalAddress(
                addressLine1 = request.addressLine1,
                addressLine2 = request.addressLine2,
                city = request.city,
                postalCode = request.postalCode,
                country = request.country,
            ),
            contactFullName = request.contactFullName,
            websiteOverride = request.website,
            contactEmail = request.contactEmail,
            contactPhoneNumber = request.contactPhoneNumber,
            activePaymentChannels = request.activePaymentChannels.toMutableSet(),
            location = request.toLocation(),
        )
        val saved = merchantBranchRepository.save(branch)
        if (active) {
            eventPublisher.publishEvent(BranchCreated(saved.id, merchantId, saved.name, merchant.businessProfile.legalBusinessName))
        }
        return saved.toResponse()
    }

    fun list(merchantId: UUID): List<BranchResponse> {
        requireMerchantExists(merchantId)
        return merchantBranchRepository
            .findAllByMerchantApplicationId(merchantId)
            .map { it.toResponse() }
    }

    fun get(merchantId: UUID, branchId: UUID): BranchResponse {
        requireMerchantExists(merchantId)
        val branch = merchantBranchRepository.findByIdAndActiveTrue(branchId)
            ?: throw NoSuchElementException("Branch not found: $branchId")
        if (branch.merchantApplicationId != merchantId) {
            throw NoSuchElementException("Branch not found: $branchId")
        }
        return branch.toResponse()
    }

    fun findByName(merchantId: UUID, branchName: String): BranchResponse? {
        requireMerchantExists(merchantId)
        return merchantBranchRepository.findByNameAndMerchantApplicationIdAndActiveTrue(branchName, merchantId)?.toResponse()
    }

    fun findByNameIncludingInactive(merchantId: UUID, branchName: String): BranchResponse? {
        requireMerchantExists(merchantId)
        return merchantBranchRepository.findByNameAndMerchantApplicationId(branchName, merchantId)?.toResponse()
    }

    fun upsertByName(
        merchantId: UUID,
        branchName: String,
        addressLine1: String? = null,
        addressLine2: String? = null,
        city: String? = null,
        postalCode: String? = null,
        country: String? = null,
        contactFullName: String? = null,
        contactEmail: String? = null,
        contactPhoneNumber: String? = null,
        activePaymentChannels: Set<MerchantPaymentChannel>? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        geofenceRadiusMeters: Int? = null,
        googlePlaceId: String? = null,
    ): BranchResponse {
        require(branchName.isNotBlank()) { "Branch name must not be blank" }
        require((latitude == null) == (longitude == null)) {
            "latitude and longitude must both be provided or both be omitted"
        }
        geofenceRadiusMeters?.let { require(it > 0) { "geofenceRadiusMeters must be positive" } }
        requireMerchantExists(merchantId)

        val existing = merchantBranchRepository.findByNameAndMerchantApplicationId(branchName, merchantId)
        if (existing == null) {
            if (latitude == null && (geofenceRadiusMeters != null || googlePlaceId != null)) {
                throw IllegalArgumentException(
                    "latitude and longitude are required when setting geofenceRadiusMeters or googlePlaceId on a new branch"
                )
            }
            return create(
                merchantId,
                CreateBranchRequest(
                    name = branchName,
                    addressLine1 = addressLine1,
                    addressLine2 = addressLine2,
                    city = city,
                    postalCode = postalCode,
                    country = country,
                    contactFullName = contactFullName,
                    contactEmail = contactEmail,
                    contactPhoneNumber = contactPhoneNumber,
                    activePaymentChannels = activePaymentChannels ?: emptySet(),
                    latitude = latitude,
                    longitude = longitude,
                    geofenceRadiusMeters = geofenceRadiusMeters,
                    googlePlaceId = googlePlaceId
                ),
                active = false
            )
        }

        if (!hasBranchDetails(
                addressLine1 = addressLine1,
                addressLine2 = addressLine2,
                city = city,
                postalCode = postalCode,
                country = country,
                contactFullName = contactFullName,
                websiteOverride = null,
                contactEmail = contactEmail,
                contactPhoneNumber = contactPhoneNumber,
                activePaymentChannels = activePaymentChannels,
                latitude = latitude,
                longitude = longitude,
                geofenceRadiusMeters = geofenceRadiusMeters,
                googlePlaceId = googlePlaceId
            )
        ) {
            return existing.toResponse()
        }

        existing.applyBranchDetails(
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            postalCode = postalCode,
            country = country,
            contactFullName = contactFullName,
            contactEmail = contactEmail,
            contactPhoneNumber = contactPhoneNumber,
            activePaymentChannels = activePaymentChannels,
            latitude = latitude,
            longitude = longitude,
            geofenceRadiusMeters = geofenceRadiusMeters,
            googlePlaceId = googlePlaceId
        )
        return merchantBranchRepository.save(existing).toResponse()
    }

    fun update(merchantId: UUID, branchId: UUID, request: UpdateBranchRequest): BranchResponse {
        require(request.name.isNotBlank()) { "Branch name must not be blank" }
        require((request.latitude == null) == (request.longitude == null)) {
            "latitude and longitude must both be provided or both be omitted"
        }
        request.geofenceRadiusMeters?.let { require(it > 0) { "geofenceRadiusMeters must be positive" } }
        val merchant = requireMerchant(merchantId)

        val branch = merchantBranchRepository.findById(branchId)
            .orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        if (branch.merchantApplicationId != merchantId) {
            throw NoSuchElementException("Branch not found: $branchId")
        }

        val previousName = branch.name
        val wasInactive = !branch.active
        branch.updateDetails(
            name = request.name,
            active = request.active,
            addressLine1 = request.addressLine1,
            addressLine2 = request.addressLine2,
            city = request.city,
            postalCode = request.postalCode,
            country = request.country,
            contactFullName = request.contactFullName,
            websiteOverride = request.website,
            contactEmail = request.contactEmail,
            contactPhoneNumber = request.contactPhoneNumber,
            activePaymentChannels = request.activePaymentChannels,
            location = request.toLocation(),
        )
        validateActiveBranch(branch)

        val saved = merchantBranchRepository.saveAndFlush(branch)
        if (wasInactive && saved.active) {
            eventPublisher.publishEvent(BranchCreated(saved.id, merchantId, saved.name, merchant.businessProfile.legalBusinessName))
        } else if (saved.name != previousName) {
            eventPublisher.publishEvent(BranchNameUpdated(saved.id, merchantId, saved.name))
        }
        return saved.toResponse()
    }

    fun updateImage(merchantId: UUID, branchId: UUID, storageKey: String): BranchResponse {
        require(storageKey.isNotBlank()) { "storageKey must not be blank" }
        requireMerchantExists(merchantId)
        val branch = merchantBranchRepository.findById(branchId)
            .orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        if (branch.merchantApplicationId != merchantId) {
            throw NoSuchElementException("Branch not found: $branchId")
        }
        branch.updateImage(storageKey)
        return merchantBranchRepository.save(branch).toResponse()
    }

    private fun requireMerchant(merchantId: UUID) =
        merchantApplicationRepository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }

    private fun requireMerchantExists(merchantId: UUID) {
        if (!merchantApplicationRepository.existsById(merchantId)) {
            throw NoSuchElementException("Merchant not found: $merchantId")
        }
    }

    private fun signedUrl(storageKey: String?): String? =
        storageKey?.let { runCatching { storageService.presignDownload(it) }.getOrNull() }

    private fun hasBranchDetails(
        addressLine1: String?,
        addressLine2: String?,
        city: String?,
        postalCode: String?,
        country: String?,
        contactFullName: String?,
        websiteOverride: String?,
        contactEmail: String?,
        contactPhoneNumber: String?,
        activePaymentChannels: Set<MerchantPaymentChannel>?,
        latitude: Double?,
        longitude: Double?,
        geofenceRadiusMeters: Int?,
        googlePlaceId: String?
    ): Boolean {
        return listOf(
            addressLine1, addressLine2, city, postalCode, country,
            contactFullName, contactEmail, contactPhoneNumber, activePaymentChannels,
            latitude, longitude, geofenceRadiusMeters, googlePlaceId
        ).any { it != null }
    }

    private fun MerchantBranch.toResponse() = BranchResponse(
        id = id,
        merchantId = merchantApplicationId,
        branchCode = branchCode,
        name = name,
        active = active,
        addressLine1 = address?.addressLine1,
        addressLine2 = address?.addressLine2,
        city = address?.city,
        postalCode = address?.postalCode,
        country = address?.country,
        contactFullName = contactFullName,
        website = websiteOverride,
        contactEmail = contactEmail,
        contactPhoneNumber = contactPhoneNumber,
        activePaymentChannels = activePaymentChannels.toSet(),
        latitude = location?.latitude,
        longitude = location?.longitude,
        geofenceRadiusMeters = location?.geofenceRadiusMeters,
        googlePlaceId = location?.googlePlaceId,
        placeFormattedAddress = location?.placeFormattedAddress,
        placeRating = location?.placeRating,
        placeReviewCount = location?.placeReviewCount,
        placeEnrichmentStatus = location?.placeEnrichmentStatus,
        placeEnrichedAt = location?.placeEnrichedAt,
        imageUrl = signedUrl(imageStorageKey),
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun CreateBranchRequest.toLocation(): MerchantLocation? =
        toLocation(latitude, longitude, geofenceRadiusMeters, googlePlaceId)

    private fun UpdateBranchRequest.toLocation(): MerchantLocation? =
        toLocation(latitude, longitude, geofenceRadiusMeters, googlePlaceId)

    private fun toLocation(
        latitude: Double?,
        longitude: Double?,
        geofenceRadiusMeters: Int?,
        googlePlaceId: String?,
    ): MerchantLocation? {
        if (latitude == null || longitude == null) return null
        return MerchantLocation(
            latitude = latitude,
            longitude = longitude,
            geofenceRadiusMeters = geofenceRadiusMeters ?: geofenceProperties.defaultRadiusMeters,
            googlePlaceId = googlePlaceId,
            placeEnrichmentStatus = googlePlaceId?.let { "PENDING" }
        )
    }

    private fun MerchantBranch.applyBranchDetails(
        addressLine1: String?,
        addressLine2: String?,
        city: String?,
        postalCode: String?,
        country: String?,
        contactFullName: String?,
        contactEmail: String?,
        contactPhoneNumber: String?,
        activePaymentChannels: Set<MerchantPaymentChannel>?,
        latitude: Double?,
        longitude: Double?,
        geofenceRadiusMeters: Int?,
        googlePlaceId: String?
    ): MerchantBranch {
        val currentAddress = this.address
        this.address = PostalAddress(
            addressLine1 = addressLine1 ?: currentAddress?.addressLine1,
            addressLine2 = addressLine2 ?: currentAddress?.addressLine2,
            city = city ?: currentAddress?.city,
            postalCode = postalCode ?: currentAddress?.postalCode,
            country = country ?: currentAddress?.country,
        )
        contactFullName?.let { this.contactFullName = it }
        contactEmail?.let { this.contactEmail = it }
        contactPhoneNumber?.let { this.contactPhoneNumber = it }
        activePaymentChannels?.let { this.activePaymentChannels = it.toMutableSet() }

        val hasCoordinateUpdate = latitude != null && longitude != null
        if (!hasCoordinateUpdate && location == null && (geofenceRadiusMeters != null || googlePlaceId != null)) {
            throw IllegalArgumentException(
                "latitude and longitude are required when setting geofenceRadiusMeters or googlePlaceId on a branch without location"
            )
        }

        val currentLocation = location
        location = when {
            hasCoordinateUpdate -> MerchantLocation(
                latitude = requireNotNull(latitude),
                longitude = requireNotNull(longitude),
                geofenceRadiusMeters = geofenceRadiusMeters ?: currentLocation?.geofenceRadiusMeters ?: geofenceProperties.defaultRadiusMeters,
                googlePlaceId = googlePlaceId ?: currentLocation?.googlePlaceId,
                placeEnrichmentStatus = enrichmentStatusFor(googlePlaceId, currentLocation)
            )

            currentLocation != null -> currentLocation.copy(
                geofenceRadiusMeters = geofenceRadiusMeters ?: currentLocation.geofenceRadiusMeters,
                googlePlaceId = googlePlaceId ?: currentLocation.googlePlaceId,
                placeEnrichmentStatus = enrichmentStatusFor(googlePlaceId, currentLocation)
            )

            else -> null
        }
        updatedAt = Instant.now()
        return this
    }

    private fun validateActiveBranch(branch: MerchantBranch) {
        if (!branch.active) return
        require(branch.address?.addressLine1?.isNotBlank() == true) { "addressLine1 must not be blank for active branches" }
        require(branch.address?.city?.isNotBlank() == true) { "city must not be blank for active branches" }
        require(branch.address?.postalCode?.isNotBlank() == true) { "postalCode must not be blank for active branches" }
        require(branch.address?.country?.isNotBlank() == true) { "country must not be blank for active branches" }
        val location = requireNotNull(branch.location) { "location is required for active branches" }
        require(location.geofenceRadiusMeters > 0) { "geofenceRadiusMeters must be positive for active branches" }
    }

    private fun enrichmentStatusFor(googlePlaceId: String?, currentLocation: MerchantLocation?): String? {
        return when {
            googlePlaceId == null -> currentLocation?.placeEnrichmentStatus
            googlePlaceId != currentLocation?.googlePlaceId -> "PENDING"
            else -> currentLocation.placeEnrichmentStatus
        }
    }
}
