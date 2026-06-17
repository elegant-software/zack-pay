package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant

@Embeddable
data class SupportingMaterial(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: SupportingMaterialType,

    @Column(nullable = false)
    val fileName: String,

    @Column(nullable = false)
    val storageKey: String,

    @Column(nullable = false)
    val uploadedAt: Instant = Instant.now()
)

enum class SupportingMaterialType {
    BUSINESS_REGISTRATION,
    PROOF_OF_ADDRESS,
    IDENTITY_DOCUMENT,
    BANK_ACCOUNT_PROOF,
    OTHER
}
