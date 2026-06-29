package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class MerchantAccessPolicyTest {

    private val policy = MerchantAccessPolicy()

    private fun merchant(merchantCode: String) = Merchant(
        merchantName = "Test Merchant",
        businessType = "RETAIL",
        operatingCountry = "DE",
    ).also { /* merchantCode is auto-generated but we test canRead by code */ }

    @Test
    fun `reviewer can read any merchant`() {
        policy.requireReadAccess(
            MerchantActor(
                actorId = "reviewer-1",
                role = MerchantActorRole.OPERATIONS_REVIEWER,
                merchantApplicationReference = null
            ),
            Merchant(merchantName = "Any", businessType = "RETAIL", operatingCountry = "DE")
        )
    }

    @Test
    fun `review access is limited to reviewer roles`() {
        assertFailsWith<IllegalArgumentException> {
            policy.requireReviewAccess(
                MerchantActor(
                    actorId = "merchant-1",
                    role = MerchantActorRole.MERCHANT_APPLICANT,
                    merchantApplicationReference = "SOME-CODE"
                )
            )
        }
    }
}
