package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A Kiosk session token. Only its SHA-256 hash is stored; deleting the row revokes the token. */
@Entity
@Table(name = "merchant_kiosk_tokens", schema = "blitzpay")
class MerchantKioskToken(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_id", nullable = false, updatable = false)
    val merchantId: UUID,

    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: UUID,

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    val tokenHash: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false, updatable = false)
    val expiresAt: Instant,
)
