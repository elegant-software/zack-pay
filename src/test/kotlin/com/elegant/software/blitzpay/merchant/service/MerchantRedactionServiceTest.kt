package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MerchantRedactionServiceTest {

    private val redactionService = MerchantRedactionService()

    @Test
    fun `redacts contact details`() {
        val merchant = Merchant(
            merchantName = "Acme Corp",
            businessType = "RETAIL",
            operatingCountry = "DE",
            merchantStatus = MerchantOperationalStatus.ACTIVE,
            publicEmail = "owner@acme.de",
            publicPhoneNumber = "+4915112345678",
        )
        val redacted = redactionService.redact(merchant)

        assertEquals("o***@acme.de", redacted.contactEmail)
        assertTrue(redacted.contactPhone!!.endsWith("5678"))
        assertEquals("Acme Corp", redacted.merchantName)
        assertEquals("ACTIVE", redacted.merchantStatus)
    }
}
