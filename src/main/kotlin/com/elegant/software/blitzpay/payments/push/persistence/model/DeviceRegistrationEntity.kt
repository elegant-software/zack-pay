package com.elegant.software.blitzpay.payments.push.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

enum class DevicePlatform { IOS, ANDROID }

@Entity
@Table(
    name = "push_device_registration",
    indexes = [
        Index(name = "ux_push_device_registration_token", columnList = "expo_push_token", unique = true),
        Index(name = "ix_push_device_registration_order_id", columnList = "order_id"),
        Index(name = "ix_push_device_registration_payment_request", columnList = "payment_request_id"),
        Index(name = "ix_push_device_registration_payer_ref", columnList = "payer_ref"),
    ],
)
class DeviceRegistrationEntity(
    @Id
    @Column(name = "id", nullable = false)
    var id: UUID,

    @Column(name = "payment_request_id")
    var paymentRequestId: UUID? = null,

    @Column(name = "order_id", nullable = false, length = 64)
    var orderId: String,

    @Column(name = "payer_ref", length = 128)
    var payerRef: String? = null,

    @Column(name = "expo_push_token", nullable = false, length = 256)
    var expoPushToken: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", length = 16)
    var platform: DevicePlatform? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),

    @Column(name = "invalid", nullable = false)
    var invalid: Boolean = false,
)

interface DeviceRegistrationRepository : JpaRepository<DeviceRegistrationEntity, UUID> {
    fun countByOrderIdAndInvalidFalse(orderId: String): Long
    fun findByOrderIdAndInvalidFalse(orderId: String): List<DeviceRegistrationEntity>
    fun findByPaymentRequestIdAndInvalidFalse(paymentRequestId: UUID): List<DeviceRegistrationEntity>
    fun findByExpoPushToken(expoPushToken: String): DeviceRegistrationEntity?
    fun deleteByExpoPushToken(expoPushToken: String): Long
}
