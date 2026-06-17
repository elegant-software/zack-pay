package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.support.MerchantTestFixtureLoader
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class MerchantAccessPolicyTest {

    private val policy = MerchantAccessPolicy()

    @Test
    fun `merchant applicant can read own application`() {
        policy.requireReadAccess(
            MerchantActor(
                actorId = "merchant-1",
                role = MerchantActorRole.MERCHANT_APPLICANT,
                merchantApplicationReference = "MO-ACCESS-1"
            ),
            MerchantTestFixtureLoader.merchantApplication(applicationReference = "MO-ACCESS-1")
        )
    }

    @Test
    fun `merchant applicant cannot read someone elses application`() {
        assertFailsWith<IllegalArgumentException> {
            policy.requireReadAccess(
                MerchantActor(
                    actorId = "merchant-2",
                    role = MerchantActorRole.MERCHANT_APPLICANT,
                    merchantApplicationReference = "OTHER-REF"
                ),
                MerchantTestFixtureLoader.merchantApplication(applicationReference = "MO-ACCESS-1")
            )
        }
    }

    @Test
    fun `review access is limited to reviewer roles`() {
        assertFailsWith<IllegalArgumentException> {
            policy.requireReviewAccess(
                MerchantActor(
                    actorId = "merchant-1",
                    role = MerchantActorRole.MERCHANT_APPLICANT,
                    merchantApplicationReference = "MO-ACCESS-1"
                )
            )
        }
    }
}
