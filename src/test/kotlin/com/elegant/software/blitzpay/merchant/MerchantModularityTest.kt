package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.QuickpayApplication
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class MerchantModularityTest {

    private val modules = ApplicationModules.of(QuickpayApplication::class.java)

    @Test
    fun `application modules verify successfully with merchant module included`() {
        modules.verify()
    }
}
