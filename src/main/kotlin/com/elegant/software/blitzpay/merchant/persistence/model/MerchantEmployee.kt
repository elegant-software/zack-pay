package com.elegant.software.blitzpay.merchant.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchant_employees", schema = "blitzpay")
class MerchantEmployee(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "merchant_id", nullable = false, updatable = false)
    val merchantId: UUID,

    @Column(nullable = false, length = 255)
    var name: String,

    @Column(nullable = false, length = 32)
    var status: String = MerchantEntityStatus.ACTIVE,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
) {
    fun deactivate(at: Instant = Instant.now()) {
        status = MerchantEntityStatus.INACTIVE
        updatedAt = at
    }

    fun rename(newName: String, at: Instant = Instant.now()) {
        require(newName.isNotBlank()) { "name must not be blank" }
        name = newName.trim()
        updatedAt = at
    }

    val isActive: Boolean get() = status == MerchantEntityStatus.ACTIVE
}
