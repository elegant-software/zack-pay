package com.elegant.software.blitzpay.merchant.api

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantMobility
import java.time.Instant
import java.util.UUID

data class MerchantSearchRequest(
    val q: String? = null,
    val businessTypes: List<String>? = null,
    val merchantMobilities: List<MerchantMobility>? = null,
    val statuses: List<String>? = null,
    val page: Int = 0,
    val size: Int = 50,
)

data class MerchantSearchResultItem(
    val merchantId: UUID,
    val merchantName: String,
    val merchantCode: String,
    val city: String?,
    val country: String?,
    val businessType: String,
    val merchantMobility: MerchantMobility,
    val merchantStatus: String,
    val lastUpdatedAt: Instant,
)

data class MerchantSearchResponse(
    val results: List<MerchantSearchResultItem>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
