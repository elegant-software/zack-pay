package com.elegant.software.blitzpay.merchant.api

import java.util.UUID

data class PreparationStation(val id: UUID, val name: String)

/** Routing is branch-specific even though Product Categories belong to the Merchant. */
data class PreparationConfiguration(
    val stations: List<PreparationStation> = emptyList(),
    val categoryDefaults: Map<UUID, UUID> = emptyMap(),
    val productOverrides: Map<UUID, UUID> = emptyMap(),
)
