package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchant_proximity_events", schema = "blitzpay")
class ProximityEvent(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "region_id", nullable = false, length = 255)
    val regionId: String,

    @Column(name = "region_type", nullable = false, length = 16)
    val regionType: String,

    @Column(name = "source_id", nullable = false)
    val sourceId: UUID,

    @Column(name = "user_subject", length = 512)
    val userSubject: String? = null,

    @Column(name = "device_id", length = 255)
    val deviceId: String? = null,

    @Column(name = "event_type", nullable = false, length = 8)
    val eventType: String,

    @Column(name = "reported_latitude", nullable = false)
    val reportedLatitude: Double,

    @Column(name = "reported_longitude", nullable = false)
    val reportedLongitude: Double,

    @Column(name = "received_at", nullable = false)
    val receivedAt: Instant = Instant.now(),
)
