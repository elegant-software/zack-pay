package com.elegant.software.blitzpay.shopper.api

import org.springframework.modulith.NamedInterface

@NamedInterface("ShopperProfileGateway")
interface ShopperProfileGateway {
    fun findByShopperId(shopperId: String): ShopperProfileData?
}

data class ShopperProfileData(
    val name: String,
    val street: String,
    val zip: String,
    val city: String,
    val country: String,
)
