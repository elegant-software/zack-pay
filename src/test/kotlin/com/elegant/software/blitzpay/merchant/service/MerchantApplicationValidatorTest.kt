package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.support.MerchantTestFixtureLoader
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class MerchantApplicationValidatorTest {

    private val validator = MerchantApplicationValidator()

    @Test
    fun `accepts a submission-ready merchant application`() {
        validator.validateForSubmission(MerchantTestFixtureLoader.merchantApplicationWithDocuments())
    }

    @Test
    fun `rejects application without beneficial owner`() {
        val application = MerchantTestFixtureLoader.merchantApplicationWithDocuments().apply {
            people.clear()
        }

        assertFailsWith<IllegalArgumentException> {
            validator.validateForSubmission(application)
        }
    }

    @Test
    fun `rejects application without business registration document`() {
        val application = MerchantTestFixtureLoader.merchantApplicationWithDocuments().apply {
            supportingMaterials.clear()
        }

        assertFailsWith<IllegalArgumentException> {
            validator.validateForSubmission(application)
        }
    }
}
