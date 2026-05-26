package com.elegant.software.blitzpay.merchant.api

import java.time.Instant
import java.util.UUID

data class ProviderAccountResponse(
    val id: UUID,
    val pspCode: String,
    val enabled: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)
