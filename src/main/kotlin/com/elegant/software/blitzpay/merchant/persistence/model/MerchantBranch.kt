package com.elegant.software.blitzpay.merchant.domain

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "merchant_branches",
    schema = "blitzpay",
    indexes = [Index(name = "idx_merchant_branches_merchant_application_id", columnList = "merchant_application_id")]
)
class MerchantBranch(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_application_id", nullable = false, updatable = false)
    val merchantApplicationId: UUID,

    @Column(name = "branch_code", nullable = false)
    val branchCode: String = "BR-" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_application_id", insertable = false, updatable = false)
    val merchantApplication: MerchantApplication? = null,

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(nullable = false, length = 32)
    var status: String = MerchantEntityStatus.INACTIVE,

    @Embedded
    var address: PostalAddress? = null,

    @Column(name = "contact_full_name")
    var contactFullName: String? = null,

    @Column(name = "contact_email")
    var contactEmail: String? = null,

    @Column(name = "contact_phone_number")
    var contactPhoneNumber: String? = null,

    @Column(name = "website_override")
    var websiteOverride: String? = null,

    @Column(name = "image_storage_key")
    var imageStorageKey: String? = null,

    @Embedded
    var location: MerchantLocation? = null,

    @Column(name = "stripe_terminal_location_id")
    var stripeTerminalLocationId: String? = null,

    @Column(name = "timezone", length = 64)
    var timezone: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    fun deactivate(at: Instant = Instant.now()) {
        active = false
        status = MerchantEntityStatus.INACTIVE
        updatedAt = at
    }

    fun updateDetails(
        name: String,
        active: Boolean,
        addressLine1: String?,
        addressLine2: String?,
        city: String?,
        postalCode: String?,
        country: String?,
        contactFullName: String?,
        websiteOverride: String?,
        contactEmail: String?,
        contactPhoneNumber: String?,
        location: MerchantLocation?,
        at: Instant = Instant.now(),
    ) {
        this.name = name
        this.active = active
        this.status = if (active) MerchantEntityStatus.ACTIVE else MerchantEntityStatus.INACTIVE
        this.address = PostalAddress(
            addressLine1 = addressLine1,
            addressLine2 = addressLine2,
            city = city,
            postalCode = postalCode,
            country = country,
        )
        this.contactFullName = contactFullName
        this.websiteOverride = websiteOverride
        this.contactEmail = contactEmail
        this.contactPhoneNumber = contactPhoneNumber
        this.location = location
        this.updatedAt = at
    }

    fun updateImage(storageKey: String, at: Instant = Instant.now()) {
        imageStorageKey = storageKey
        updatedAt = at
    }
}
