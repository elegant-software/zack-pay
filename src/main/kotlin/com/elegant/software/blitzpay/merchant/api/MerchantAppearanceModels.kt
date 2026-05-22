package com.elegant.software.blitzpay.merchant.api

import java.time.Instant
import java.util.UUID

data class CreateAppearanceRequest(
    val declaredLatitude: Double,
    val declaredLongitude: Double,
    val startTime: Instant,
    val endTime: Instant,
    val name: String? = null,
)

data class PushLivePositionRequest(
    val latitude: Double,
    val longitude: Double,
)

data class AppearanceResponse(
    val id: UUID,
    val branchId: UUID,
    val merchantId: UUID,
    val declaredLatitude: Double,
    val declaredLongitude: Double,
    val liveLatitude: Double?,
    val liveLongitude: Double?,
    val effectiveLatitude: Double,
    val effectiveLongitude: Double,
    val startTime: Instant,
    val endTime: Instant,
    val name: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ActiveAppearanceItem(
    val appearanceId: UUID,
    val merchantId: UUID,
    val merchantName: String,
    val branchId: UUID,
    val branchName: String,
    val name: String?,
    val effectiveLatitude: Double,
    val effectiveLongitude: Double,
    val distanceMeters: Double?,
    val startTime: Instant,
    val endTime: Instant,
)

data class CurrentlyHappeningResponse(
    val appearances: List<ActiveAppearanceItem>,
)
