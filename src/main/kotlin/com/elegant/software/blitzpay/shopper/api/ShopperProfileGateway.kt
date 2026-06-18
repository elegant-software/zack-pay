package com.elegant.software.blitzpay.shopper.api

import org.springframework.modulith.NamedInterface

@NamedInterface("ShopperProfileGateway")
interface ShopperProfileGateway {
    fun findByShopperId(shopperId: String): ShopperProfileData?
    fun findByEmail(email: String): ShopperSearchResult?
}

data class ShopperProfileData(
    val name: String,
    val street: String,
    val zip: String,
    val city: String,
    val country: String,
    val email: String? = null,
)

data class ShopperSearchResult(
    val shopperAccountId: String,
    val name: String,
)
