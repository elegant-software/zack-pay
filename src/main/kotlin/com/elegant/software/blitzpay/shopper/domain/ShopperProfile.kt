package com.elegant.software.blitzpay.shopper.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "shopper_profiles", schema = "blitzpay")
class ShopperProfile(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "shopper_account_id", nullable = false, unique = true, updatable = false, length = 512)
    val shopperAccountId: String,

    @Column(name = "name", nullable = false, length = 255)
    var name: String,

    @Column(name = "street", nullable = false, length = 255)
    var street: String,

    @Column(name = "zip", nullable = false, length = 64)
    var zip: String,

    @Column(name = "city", nullable = false, length = 128)
    var city: String,

    @Column(name = "country", nullable = false, length = 64)
    var country: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
