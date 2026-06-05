package com.elegant.software.blitzpay.payments.push.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "push_shopper_device_registrations", schema = "blitzpay")
class ShopperDeviceRegistrationEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "shopper_account_id", nullable = false, length = 256)
    val shopperAccountId: String,

    @Column(name = "expo_push_token", nullable = false, length = 256)
    var expoPushToken: String,

    @Column(name = "platform", length = 16)
    var platform: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),

    @Column(name = "invalid", nullable = false)
    var invalid: Boolean = false,
)

interface ShopperDeviceRegistrationRepository : org.springframework.data.jpa.repository.JpaRepository<ShopperDeviceRegistrationEntity, UUID> {
    fun findAllByShopperAccountIdAndInvalidFalse(shopperAccountId: String): List<ShopperDeviceRegistrationEntity>
    fun findByExpoPushToken(expoPushToken: String): ShopperDeviceRegistrationEntity?
    fun deleteByExpoPushToken(expoPushToken: String): Long
}
