package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/** The Kiosk Settings of a Branch, shared by all its Kiosks. */
@Entity
@Table(name = "merchant_kiosk_settings", schema = "blitzpay")
class MerchantKioskSettings(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: UUID,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payment_choices", nullable = false, columnDefinition = "jsonb")
    var paymentChoices: List<String> = DEFAULT_PAYMENT_CHOICES,

    @Column(name = "reset_seconds", nullable = false)
    var resetSeconds: Int = DEFAULT_RESET_SECONDS,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var languages: List<String> = emptyList(),

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "advertised_product_ids", nullable = false, columnDefinition = "jsonb")
    var advertisedProductIds: List<String> = emptyList(),

    /** BCrypt hash of the Admin PIN; null = no PIN set, so no Kiosk of the Branch can open its admin menu. */
    @Column(name = "admin_pin_hash")
    var adminPinHash: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    companion object {
        val DEFAULT_PAYMENT_CHOICES = listOf("CARD", "CASH", "SCAN_TO_PAY")
        const val DEFAULT_RESET_SECONDS = 30
    }
}
