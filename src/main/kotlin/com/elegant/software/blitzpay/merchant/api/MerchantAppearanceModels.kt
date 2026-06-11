package com.elegant.software.blitzpay.merchant.api

import java.time.Instant
import java.util.UUID

enum class AppearanceStatus {
    ACTIVE,
    UPCOMING,
    COMPLETED,
    CANCELLED,
}

enum class AppearanceHistoryStatus {
    COMPLETED,
    CANCELLED,
}

data class CreateAppearanceRequest(
    val declaredLatitude: Double,
    val declaredLongitude: Double,
    val startTime: Instant,
    val endTime: Instant,
    val name: String,
)

data class AppearanceResponse(
    val id: UUID,
    val branchId: UUID,
    val branchName: String,
    val merchantId: UUID,
    val declaredLatitude: Double,
    val declaredLongitude: Double,
    val liveLatitude: Double?,
    val liveLongitude: Double?,
    val effectiveLatitude: Double,
    val effectiveLongitude: Double,
    val startTime: Instant,
    val endTime: Instant,
    val name: String,
    val createdAt: Instant,
    val cancelledAt: Instant?,
    val updatedAt: Instant,
    val status: AppearanceStatus,
)

data class AppearanceHistoryPageResponse(
    val content: List<AppearanceResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
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
