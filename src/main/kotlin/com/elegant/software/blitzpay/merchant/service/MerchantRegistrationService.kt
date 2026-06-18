package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MerchantActivated
import com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest
import com.elegant.software.blitzpay.merchant.iam.service.MerchantGroupSyncService
import com.elegant.software.blitzpay.merchant.persistence.model.BusinessProfile
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantApplication
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantMobility
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.persistence.model.PrimaryContact
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.support.MerchantObservabilitySupport
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Service
@Transactional
class MerchantRegistrationService(
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val merchantAuditTrail: MerchantAuditTrail,
    private val merchantObservabilitySupport: MerchantObservabilitySupport,
    private val eventPublisher: ApplicationEventPublisher,
    private val merchantVerticalService: MerchantVerticalService,
    private val groupSyncService: Optional<MerchantGroupSyncService>,
) {

    private val log = LoggerFactory.getLogger(MerchantRegistrationService::class.java)

    fun register(request: RegisterMerchantRequest): MerchantApplication {
        return saveRegistration(
            request = request,
            action = "register_direct",
            activateDirectly = true
        )
    }

    fun registerDraft(request: RegisterMerchantRequest): MerchantApplication {
        return saveRegistration(
            request = request,
            action = "register_draft",
            activateDirectly = false
        )
    }

    private fun saveRegistration(
        request: RegisterMerchantRequest,
        action: String,
        activateDirectly: Boolean
    ): MerchantApplication {
        merchantVerticalService.validate(request.businessProfile.businessType)

        val registrationNumber = request.businessProfile.registrationNumber
        val duplicateExists = merchantApplicationRepository.existsByBusinessProfileRegistrationNumberAndStatusIn(
            registrationNumber,
            ACTIVE_STATUSES
        )
        require(!duplicateExists) {
            "An active merchant application already exists for registration number $registrationNumber"
        }

        val applicationReference = "BLTZ-" + UUID.randomUUID().toString().take(8).uppercase()
        val now = Instant.now()

        val mobility = request.businessProfile.merchantMobility ?: MerchantMobility.BRICK_AND_MORTAR

        val application = MerchantApplication(
            applicationReference = applicationReference,
            merchantName = request.businessProfile.legalBusinessName,
            publicEmail = request.primaryContact.email,
            publicPhoneNumber = request.primaryContact.phoneNumber,
            businessProfile = BusinessProfile(
                legalBusinessName = request.businessProfile.legalBusinessName,
                businessType = request.businessProfile.businessType,
                registrationNumber = registrationNumber,
                operatingCountry = request.businessProfile.operatingCountry,
                primaryBusinessAddress = request.businessProfile.primaryBusinessAddress
            ),
            primaryContact = PrimaryContact(
                fullName = request.primaryContact.fullName,
                email = request.primaryContact.email,
                phoneNumber = request.primaryContact.phoneNumber
            ),
            merchantMobility = mobility,
        )
        if (activateDirectly) {
            application.registerDirect(now)
        } else {
            application.lastUpdatedAt = now
        }

        val saved = merchantApplicationRepository.save(application)
        merchantAuditTrail.record(
            MerchantAuditEvent(
                applicationId = saved.id,
                applicationReference = saved.applicationReference,
                actorId = SYSTEM_ACTOR,
                action = action,
                status = saved.status,
                occurredAt = now
            )
        )
        merchantObservabilitySupport.recordSuccess(action, saved.status)
        log.info("Merchant registration saved: action={} ref={} status={}", action, saved.applicationReference, saved.status)
        if (activateDirectly) {
            groupSyncService.ifPresent { it.ensureMerchantGroupExists(saved.id, saved.businessProfile.legalBusinessName) }
            eventPublisher.publishEvent(MerchantActivated(saved.id, saved.businessProfile.legalBusinessName))
        }
        return saved
    }

    @Transactional(readOnly = true)
    fun findById(merchantId: UUID): MerchantApplication =
        merchantApplicationRepository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant application not found: $merchantId") }

    @Transactional(readOnly = true)
    fun findByName(name: String): MerchantApplication? =
        merchantApplicationRepository.findByBusinessProfileLegalBusinessName(name)

    companion object {
        private const val SYSTEM_ACTOR = "system"
        private val ACTIVE_STATUSES = setOf(
            MerchantOnboardingStatus.SUBMITTED,
            MerchantOnboardingStatus.VERIFICATION,
            MerchantOnboardingStatus.SCREENING,
            MerchantOnboardingStatus.RISK_REVIEW,
            MerchantOnboardingStatus.DECISION_PENDING,
            MerchantOnboardingStatus.SETUP,
            MerchantOnboardingStatus.ACTIVE,
            MerchantOnboardingStatus.MONITORING,
            MerchantOnboardingStatus.ACTION_REQUIRED
        )
    }
}
