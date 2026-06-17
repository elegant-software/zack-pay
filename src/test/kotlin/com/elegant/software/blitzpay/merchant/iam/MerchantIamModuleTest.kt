package com.elegant.software.blitzpay.merchant.iam

import com.elegant.software.blitzpay.QuickpayApplication
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class MerchantIamModuleTest {

    private val modules = ApplicationModules.of(QuickpayApplication::class.java)

    @Test
    fun `merchant iam module passes modulith verification`() {
        modules.verify()
    }
}
