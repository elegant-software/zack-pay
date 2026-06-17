package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "merchant_provider_accounts",
    schema = "blitzpay",
    uniqueConstraints = [UniqueConstraint(columnNames = ["merchant_application_id", "psp_code"])]
)
class MerchantProviderAccount(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_application_id", nullable = false, updatable = false)
    val merchantApplicationId: UUID,

    @Column(name = "psp_code", length = 32, nullable = false, updatable = false)
    val pspCode: String,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
)
