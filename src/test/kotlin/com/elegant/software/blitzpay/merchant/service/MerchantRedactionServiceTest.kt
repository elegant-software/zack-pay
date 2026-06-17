package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.support.MerchantTestFixtureLoader
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MerchantRedactionServiceTest {

    private val redactionService = MerchantRedactionService()
    private val expectations = MerchantTestFixtureLoader.fixture.expectations

    @Test
    fun `redacts contact and registration details`() {
        val redacted = redactionService.redact(
            MerchantTestFixtureLoader.merchantApplicationWithDocuments(applicationReference = "MO-REDACT-1")
        )

        assertEquals(expectations.redactedRegistrationNumber, redacted.registrationNumber)
        assertEquals(expectations.redactedEmail, redacted.primaryContactEmail)
        assertTrue(redacted.primaryContactPhone.endsWith(expectations.redactedPhoneSuffix))
        assertEquals(1, redacted.peopleCount)
        assertEquals(1, redacted.supportingMaterialCount)
    }
}
