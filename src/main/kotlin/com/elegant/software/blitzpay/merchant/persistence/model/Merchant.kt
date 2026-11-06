package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchants", schema = "blitzpay")
class Merchant(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_code", nullable = false, unique = true)
    val merchantCode: String = "MRC-" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase(),

    @Column(name = "merchant_name", nullable = false)
    var merchantName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "merchant_status", nullable = false, length = 32)
    var merchantStatus: MerchantOperationalStatus = MerchantOperationalStatus.ACTIVE,

    @Column(name = "business_type", nullable = false)
    var businessType: String,

    @Column(name = "operating_country", nullable = false)
    var operatingCountry: String,

    @Column(name = "logo_storage_key")
    var logoStorageKey: String? = null,

    @Column(name = "website")
    var website: String? = null,

    @Embedded
    var address: PostalAddress? = null,

    @Column(name = "contact_email_public")
    var publicEmail: String? = null,

    @Column(name = "contact_phone_public")
    var publicPhoneNumber: String? = null,

    @Embedded
    var location: MerchantLocation? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "merchant_mobility", nullable = false, length = 32)
    var merchantMobility: MerchantMobility = MerchantMobility.BRICK_AND_MORTAR,

    @Column(name = "booking_interval_minutes")
    var bookingIntervalMinutes: Int? = null,

    @Column(name = "booking_lead_time_minutes")
    var bookingLeadTimeMinutes: Int? = null,

    @Column(name = "booking_horizon_days")
    var bookingHorizonDays: Int? = null,

    @Column(name = "reminder_lead_time_minutes")
    var reminderLeadTimeMinutes: Int? = null,

    @Column(name = "default_vat_percent", precision = 5, scale = 2)
    var defaultVatPercent: BigDecimal? = null,

    @Column(name = "x_handle", length = 128)
    var xHandle: String? = null,

    @Column(name = "instagram_handle", length = 128)
    var instagramHandle: String? = null,

    @Column(name = "original_menu_language", length = 8)
    var originalMenuLanguage: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column
    var lastUpdatedAt: Instant = createdAt,

    @Embedded
    var branding: MerchantBranding? = null,
) {
    fun updateLogo(storageKey: String) {
        logoStorageKey = storageKey
        touch()
    }

    fun updateLocation(newLocation: MerchantLocation) {
        location = newLocation
        touch()
    }

    fun clearLocation() {
        location = null
        touch()
    }

    private fun touch(changedAt: Instant = Instant.now()) {
        lastUpdatedAt = changedAt
    }
}
