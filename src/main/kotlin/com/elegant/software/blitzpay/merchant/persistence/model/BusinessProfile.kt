package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class BusinessProfile(
    @Column(nullable = false)
    val legalBusinessName: String,

    @Column(nullable = false)
    val businessType: String,

    @Column(nullable = false)
    val registrationNumber: String,

    @Column(nullable = false)
    val operatingCountry: String,

    @Column(nullable = false)
    val primaryBusinessAddress: String,

    @Column(name = "logo_storage_key")
    val logoStorageKey: String? = null
)
