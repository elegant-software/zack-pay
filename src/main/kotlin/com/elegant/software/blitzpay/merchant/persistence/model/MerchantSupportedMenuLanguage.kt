package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class MerchantSupportedMenuLanguageId(
    val merchantId: UUID = UUID.randomUUID(),
    val language: String = "",
) : Serializable

@Entity
@IdClass(MerchantSupportedMenuLanguageId::class)
@Table(name = "merchant_supported_menu_languages", schema = "blitzpay")
class MerchantSupportedMenuLanguage(
    @Id
    @Column(name = "merchant_id", nullable = false, updatable = false)
    val merchantId: UUID,

    @Id
    @Column(nullable = false, length = 8, updatable = false)
    val language: String,

    @Column(name = "added_at", nullable = false)
    val addedAt: Instant = Instant.now(),
)
