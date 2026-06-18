package com.elegant.software.blitzpay.shopper.web

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class UpsertShopperProfileRequest(
    val name: String,
    val street: String,
    val zip: String,
    val city: String,
    val country: String,
    val email: String? = null,
)

data class ShopperProfileResponse(
    val name: String,
    val street: String,
    val zip: String,
    val city: String,
    val country: String,
    val email: String? = null,
)
