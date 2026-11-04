package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** A named picture of a Branch's room or area on which staff place Tables. */
@Entity
@Table(name = "merchant_floor_plans", schema = "blitzpay")
class MerchantFloorPlan(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: UUID,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "image_storage_key", length = 512)
    var imageStorageKey: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
)
