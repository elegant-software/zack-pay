package com.elegant.software.blitzpay.merchant.support

import com.elegant.software.blitzpay.merchant.api.MerchantThemeRequest
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

object MerchantThemeFixtureLoader {
    fun request(): MerchantThemeRequest = requireNotNull(
        javaClass.getResourceAsStream("/testdata/merchant/theme.json")
    ).use { jacksonObjectMapper().readValue(it) }
}
