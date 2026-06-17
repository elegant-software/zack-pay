package com.elegant.software.blitzpay.merchant.infra

import com.elegant.software.blitzpay.merchant.persistence.model.BusinessProfile
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantApplication
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.persistence.model.MonitoringRecord
import com.elegant.software.blitzpay.merchant.persistence.model.Person
import com.elegant.software.blitzpay.merchant.persistence.model.PersonRole
import com.elegant.software.blitzpay.merchant.persistence.model.PrimaryContact
import com.elegant.software.blitzpay.merchant.persistence.model.ReviewDecision
import com.elegant.software.blitzpay.merchant.persistence.model.ReviewOutcome
import com.elegant.software.blitzpay.merchant.persistence.model.RiskAssessment
import com.elegant.software.blitzpay.merchant.persistence.model.RiskLevel
import com.elegant.software.blitzpay.merchant.persistence.model.SupportingMaterial
import com.elegant.software.blitzpay.merchant.persistence.model.SupportingMaterialType
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.QuickpayApplication
import com.truelayer.java.TrueLayerClient
import org.junit.jupiter.api.Test
import org.keycloak.admin.client.Keycloak
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(classes = [QuickpayApplication::class])
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class MerchantApplicationRepositoryTest(
    @Autowired private val merchantApplicationRepository: MerchantApplicationRepository
) {
    @MockitoBean
    private lateinit var trueLayerClient: TrueLayerClient
    @MockitoBean
    private lateinit var keycloak: Keycloak
    companion object {
        @Container
        private val postgres = PostgreSQLContainer("postgres:17-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Test
    fun `persists merchant application aggregate with embedded collections and monitoring record`() {
        val application = MerchantApplication(
            applicationReference = "MO-987654",
            businessProfile = BusinessProfile(
                legalBusinessName = "Northwind GmbH",
                businessType = "RETAIL",
                registrationNumber = "HRB999999",
                operatingCountry = "DE",
                primaryBusinessAddress = "Unter den Linden 7, Berlin"
            ),
            primaryContact = PrimaryContact(
                fullName = "Ada Merchant",
                email = "ada@northwind.example",
                phoneNumber = "+49111222333"
            ),
            status = MerchantOnboardingStatus.SETUP
        ).apply {
            people += Person(
                fullName = "Ada Merchant",
                role = PersonRole.BENEFICIAL_OWNER,
                countryOfResidence = "DE",
                ownershipPercentage = 75
            )
            supportingMaterials += SupportingMaterial(
                type = SupportingMaterialType.BUSINESS_REGISTRATION,
                fileName = "registration.pdf",
                storageKey = "merchant/MO-987654/registration.pdf"
            )
            riskAssessment = RiskAssessment(
                level = RiskLevel.MEDIUM,
                score = 57,
                rationale = "cross-border activity",
                assessedAt = Instant.parse("2026-03-23T12:00:00Z")
            )
            reviewDecisions += ReviewDecision(
                outcome = ReviewOutcome.APPROVED,
                reason = "approved by operations",
                reviewerId = "ops-1"
            )
            monitoringRecord = MonitoringRecord(lastTriggerReason = "activation handoff complete")
        }

        merchantApplicationRepository.saveAndFlush(application)

        val saved = merchantApplicationRepository.findByApplicationReference("MO-987654")

        assertNotNull(saved)
        assertEquals("Northwind GmbH", saved.businessProfile.legalBusinessName)
        assertEquals(1, saved.people.size)
        assertEquals(1, saved.supportingMaterials.size)
        assertEquals(1, saved.reviewDecisions.size)
        assertNotNull(saved.monitoringRecord)
        assertTrue(
            merchantApplicationRepository.existsByBusinessProfileRegistrationNumberAndStatusIn(
                "HRB999999",
                setOf(MerchantOnboardingStatus.SETUP, MerchantOnboardingStatus.ACTIVE)
            )
        )
    }
}
