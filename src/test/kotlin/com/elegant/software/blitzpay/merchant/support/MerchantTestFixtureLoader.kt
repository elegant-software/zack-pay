package com.elegant.software.blitzpay.merchant.support

import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.api.BrandingSettings
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

object MerchantTestFixtureLoader {

    fun brandingSettings(): BrandingSettings = requireNotNull(
        javaClass.getResourceAsStream("/testdata/merchant/branding-settings.json")
    ).use { jacksonObjectMapper().readValue(it) }

    fun merchant(
        merchantName: String = "Acme Corp",
        businessType: String = "RETAIL",
        operatingCountry: String = "DE",
        merchantStatus: MerchantOperationalStatus = MerchantOperationalStatus.ACTIVE,
    ): Merchant = Merchant(
        merchantName = merchantName,
        businessType = businessType,
        operatingCountry = operatingCountry,
        merchantStatus = merchantStatus,
    )
}
