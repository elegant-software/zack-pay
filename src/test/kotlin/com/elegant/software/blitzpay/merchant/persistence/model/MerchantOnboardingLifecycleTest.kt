package com.elegant.software.blitzpay.merchant.persistence.model

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MerchantOnboardingLifecycleTest {

    @Test
    fun `allows the primary onboarding path through monitoring`() {
        assertTrue(MerchantOnboardingLifecycle.canTransition(MerchantOnboardingStatus.DRAFT, MerchantOnboardingStatus.SUBMITTED))
        assertTrue(MerchantOnboardingLifecycle.canTransition(MerchantOnboardingStatus.SUBMITTED, MerchantOnboardingStatus.VERIFICATION))
        assertTrue(MerchantOnboardingLifecycle.canTransition(MerchantOnboardingStatus.VERIFICATION, MerchantOnboardingStatus.SCREENING))
        assertTrue(MerchantOnboardingLifecycle.canTransition(MerchantOnboardingStatus.SCREENING, MerchantOnboardingStatus.RISK_REVIEW))
        assertTrue(MerchantOnboardingLifecycle.canTransition(MerchantOnboardingStatus.RISK_REVIEW, MerchantOnboardingStatus.DECISION_PENDING))
        assertTrue(MerchantOnboardingLifecycle.canTransition(MerchantOnboardingStatus.DECISION_PENDING, MerchantOnboardingStatus.SETUP))
        assertTrue(MerchantOnboardingLifecycle.canTransition(MerchantOnboardingStatus.SETUP, MerchantOnboardingStatus.ACTIVE))
        assertTrue(MerchantOnboardingLifecycle.canTransition(MerchantOnboardingStatus.ACTIVE, MerchantOnboardingStatus.MONITORING))
    }

    @Test
    fun `allows action required from review and activation stages`() {
        assertContains(
            MerchantOnboardingLifecycle.allowedNextStatuses(MerchantOnboardingStatus.VERIFICATION),
            MerchantOnboardingStatus.ACTION_REQUIRED
        )
        assertContains(
            MerchantOnboardingLifecycle.allowedNextStatuses(MerchantOnboardingStatus.SETUP),
            MerchantOnboardingStatus.ACTION_REQUIRED
        )
        assertContains(
            MerchantOnboardingLifecycle.allowedNextStatuses(MerchantOnboardingStatus.MONITORING),
            MerchantOnboardingStatus.ACTION_REQUIRED
        )
    }

    @Test
    fun `allows direct registration path from DRAFT to ACTIVE`() {
        assertTrue(MerchantOnboardingLifecycle.canTransition(MerchantOnboardingStatus.DRAFT, MerchantOnboardingStatus.ACTIVE))
    }

    @Test
    fun `rejects skipping directly from submission to active`() {
        assertFalse(MerchantOnboardingLifecycle.canTransition(MerchantOnboardingStatus.SUBMITTED, MerchantOnboardingStatus.ACTIVE))
        assertFailsWith<IllegalArgumentException> {
            MerchantOnboardingLifecycle.requireTransition(
                MerchantOnboardingStatus.SUBMITTED,
                MerchantOnboardingStatus.ACTIVE
            )
        }
    }

    @Test
    fun `does not allow transitions from rejected`() {
        assertTrue(MerchantOnboardingLifecycle.allowedNextStatuses(MerchantOnboardingStatus.REJECTED).isEmpty())
        assertFalse(MerchantOnboardingLifecycle.canTransition(MerchantOnboardingStatus.REJECTED, MerchantOnboardingStatus.SUBMITTED))
    }
}
