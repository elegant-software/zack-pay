package com.elegant.software.blitzpay.merchant.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchant_product_categories", schema = "blitzpay")
class MerchantProductCategory(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_application_id", nullable = false, updatable = false)
    val merchantApplicationId: UUID,

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(name = "estimated_duration_minutes")
    var estimatedDurationMinutes: Int? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(name.trim().length <= 100) { "name must be <= 100 characters" }
        name = name.trim()
        estimatedDurationMinutes?.let {
            require(it > 0) { "estimatedDurationMinutes must be positive" }
            require(it <= 480) { "estimatedDurationMinutes must be <= 480 (8 hours)" }
        }
    }

    fun rename(newName: String, at: Instant = Instant.now()) {
        require(newName.isNotBlank()) { "name must not be blank" }
        require(newName.trim().length <= 100) { "name must be <= 100 characters" }
        name = newName.trim()
        updatedAt = at
    }

    fun updateDuration(durationMinutes: Int?, at: Instant = Instant.now()) {
        durationMinutes?.let {
            require(it > 0) { "estimatedDurationMinutes must be positive" }
            require(it <= 480) { "estimatedDurationMinutes must be <= 480 (8 hours)" }
        }
        estimatedDurationMinutes = durationMinutes
        updatedAt = at
    }
}
