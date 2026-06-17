package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

data class MerchantOfferingAssignmentId(
    val merchantApplicationId: UUID = UUID.randomUUID(),
    val offeringCode: String = "",
) : Serializable

@Entity
@IdClass(MerchantOfferingAssignmentId::class)
@Table(name = "merchant_application_offerings", schema = "blitzpay")
class MerchantOfferingAssignment(
    @Id
    @Column(name = "merchant_application_id", nullable = false, updatable = false)
    val merchantApplicationId: UUID,

    @Id
    @Column(name = "offering_code", nullable = false, length = 64, updatable = false)
    val offeringCode: String,

    @Column(name = "enabled_at", nullable = false)
    val enabledAt: Instant = Instant.now(),
)
