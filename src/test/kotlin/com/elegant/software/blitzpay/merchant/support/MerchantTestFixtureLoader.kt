package com.elegant.software.blitzpay.merchant.support

import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus

object MerchantTestFixtureLoader {

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
