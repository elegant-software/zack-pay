package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.ProximityEvent
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface ProximityEventRepository : JpaRepository<ProximityEvent, UUID> {

    fun existsByRegionIdAndEventTypeAndUserSubjectAndReceivedAtAfter(
        regionId: String,
        eventType: String,
        userSubject: String,
        since: Instant,
    ): Boolean

    fun existsByRegionIdAndEventTypeAndDeviceIdAndReceivedAtAfter(
        regionId: String,
        eventType: String,
        deviceId: String,
        since: Instant,
    ): Boolean
}
