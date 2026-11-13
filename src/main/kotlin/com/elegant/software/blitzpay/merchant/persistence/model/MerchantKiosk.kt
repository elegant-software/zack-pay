package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One Kiosk of a Branch. Its Kiosk Login is a Keycloak user named [username]; whether it is enabled and its password
 * live in Keycloak only.
 */
@Entity
@Table(name = "merchant_kiosks", schema = "blitzpay")
class MerchantKiosk(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: UUID,

    /** 1, 2, 3… within the Branch; part of [username]. */
    @Column(nullable = false, updatable = false)
    val number: Int,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false, updatable = false, length = 128)
    val username: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
