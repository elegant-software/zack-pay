package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * The Kiosk of a Branch: its shared Kiosk Login and its Kiosk Settings. Only the BCrypt hash of the password is kept;
 * the password itself is shown once when the login is enabled or reset.
 */
@Entity
@Table(name = "merchant_kiosks", schema = "blitzpay")
class MerchantKiosk(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: UUID,

    @Column(length = 128)
    var login: String? = null,

    @Column(name = "password_hash", length = 100)
    var passwordHash: String? = null,

    @Column(nullable = false)
    var enabled: Boolean = false,

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
