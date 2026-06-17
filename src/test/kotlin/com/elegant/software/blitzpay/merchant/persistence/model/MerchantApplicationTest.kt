package com.elegant.software.blitzpay.merchant.persistence.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MerchantApplicationTest {

    @Test
    fun `registerDirect sets status to ACTIVE and records activatedAt`() {
        val application = merchantApplication()
        val activatedAt = Instant.parse("2026-03-29T10:00:00Z")

        application.registerDirect(activatedAt)

        assertEquals(MerchantOnboardingStatus.ACTIVE, application.status)
        assertEquals(activatedAt, application.submittedAt)
        assertEquals(activatedAt, application.lastUpdatedAt)
    }

    @Test
    fun `registerDirect rejects transition from non-DRAFT status`() {
        val application = merchantApplication(status = MerchantOnboardingStatus.SUBMITTED)

        val ex = assertThrows<IllegalArgumentException> {
            application.registerDirect()
        }
        assert(ex.message!!.contains("SUBMITTED"))
    }

    @Test
    fun `submit moves application from draft to submitted and stamps submission time`() {
        val application = merchantApplication()
        val submittedAt = Instant.parse("2026-03-23T10:15:30Z")

        application.submit(submittedAt)

        assertEquals(MerchantOnboardingStatus.SUBMITTED, application.status)
        assertEquals(submittedAt, application.submittedAt)
        assertEquals(submittedAt, application.lastUpdatedAt)
    }

    @Test
    fun `recording an approval moves application into setup and stores decision`() {
        val application = merchantApplication(status = MerchantOnboardingStatus.DECISION_PENDING)

        application.recordDecision(
            outcome = ReviewOutcome.APPROVED,
            reason = "compliance checks passed",
            reviewerId = "reviewer-1"
        )

        assertEquals(MerchantOnboardingStatus.SETUP, application.status)
        assertEquals(1, application.reviewDecisions.size)
        assertEquals(ReviewOutcome.APPROVED, application.reviewDecisions.single().outcome)
    }

    @Test
    fun `starting monitoring attaches record and moves application into monitoring`() {
        val application = merchantApplication(status = MerchantOnboardingStatus.ACTIVE)
        val monitoringRecord = MonitoringRecord(lastTriggerReason = "initial activation")

        application.startMonitoring(monitoringRecord)

        assertEquals(MerchantOnboardingStatus.MONITORING, application.status)
        assertNotNull(application.monitoringRecord)
        assertEquals(MonitoringStatus.ACTIVE, application.monitoringRecord?.status)
    }

    @Test
    fun `updateProfile keeps merchant name and public contact aligned with operational fields`() {
        val application = merchantApplication()

        application.updateProfile(
            legalBusinessName = "Operational Merchant",
            primaryBusinessAddress = "Neue Strasse 3, Berlin",
            primaryContact = PrimaryContact(
                fullName = "Owner Name",
                email = "owner@example.com",
                phoneNumber = "+491234567"
            )
        )

        assertEquals("Operational Merchant", application.merchantName)
        assertEquals("owner@example.com", application.publicEmail)
        assertEquals("+491234567", application.publicPhoneNumber)
        assertTrue(application.merchantCode.startsWith("MRC-"))
    }

    @Test
    fun `merchantStatus defaults to ACTIVE and is independent from onboarding status`() {
        val application = merchantApplication(status = MerchantOnboardingStatus.DRAFT)

        assertEquals(MerchantOperationalStatus.ACTIVE, application.merchantStatus)
        assertEquals(MerchantOnboardingStatus.DRAFT, application.status)
    }

    @Test
    fun `merchantStatus can be changed independently of onboarding status`() {
        val application = merchantApplication(status = MerchantOnboardingStatus.ACTIVE)

        application.merchantStatus = MerchantOperationalStatus.INACTIVE

        assertEquals(MerchantOperationalStatus.INACTIVE, application.merchantStatus)
        assertEquals(MerchantOnboardingStatus.ACTIVE, application.status)
    }

    @Test
    fun `merchantCode is stable and starts with MRC-`() {
        val application = merchantApplication()
        val originalCode = application.merchantCode

        application.updateProfile(
            legalBusinessName = "Updated Name",
            primaryBusinessAddress = "New Address",
            primaryContact = PrimaryContact("Name", "e@example.com", "+49000")
        )

        assertTrue(application.merchantCode.startsWith("MRC-"))
        assertEquals(originalCode, application.merchantCode)
    }

    private fun merchantApplication(
        status: MerchantOnboardingStatus = MerchantOnboardingStatus.DRAFT
    ) = MerchantApplication(
        applicationReference = "MO-123456",
        businessProfile = BusinessProfile(
            legalBusinessName = "Acme GmbH",
            businessType = "RETAIL",
            registrationNumber = "HRB123456",
            operatingCountry = "DE",
            primaryBusinessAddress = "Alexanderplatz 1, Berlin"
        ),
        primaryContact = PrimaryContact(
            fullName = "Mina Example",
            email = "mina@example.com",
            phoneNumber = "+49123456789"
        ),
        status = status
    )
}
