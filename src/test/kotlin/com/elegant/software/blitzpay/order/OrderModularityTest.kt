package com.elegant.software.blitzpay.order

import com.elegant.software.blitzpay.QuickpayApplication
import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules

class OrderModularityTest {
    private val modules = ApplicationModules.of(QuickpayApplication::class.java)

    @Test
    fun `application modules verify successfully with order module included`() {
        modules.verify()
    }
}
