package com.elegant.software.blitzpay.merchant.persistence.model

import com.elegant.software.blitzpay.merchant.api.FloorArea
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
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

    /** Floor Areas traced over the picture; the live floor draws these instead of the picture when there are any. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "areas", nullable = false, columnDefinition = "jsonb")
    var areas: List<FloorArea> = emptyList(),

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
)
