package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.ActiveBranchInfo
import com.elegant.software.blitzpay.merchant.api.BranchLocationInfo
import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.merchant.api.MerchantSummary
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantApplication
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.persistence.model.MonitoringRecord
import com.elegant.software.blitzpay.merchant.persistence.model.ReviewOutcome
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.merchant.support.MerchantObservabilitySupport
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class MerchantOnboardingService(
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
    private val merchantOfferingAssignmentRepository: MerchantOfferingAssignmentRepository,
    private val merchantApplicationValidator: MerchantApplicationValidator,
    private val merchantAuditTrail: MerchantAuditTrail,
    private val merchantObservabilitySupport: MerchantObservabilitySupport
) : MerchantGateway {

    override fun submit(applicationId: UUID, submittedAt: Instant): MerchantSummary {
        return observe("submit") {
            val application = getApplication(applicationId)
            merchantApplicationValidator.validateForSubmission(application)
            ensureNoDuplicateActiveApplication(application)
            application.submit(submittedAt)
            val saved = merchantApplicationRepository.save(application)
            audit(saved, SYSTEM_ACTOR, "submit", submittedAt)
            saved
        }.toSummary()
    }

    override fun transition(
        applicationId: UUID,
        nextStatus: MerchantOnboardingStatus,
        changedAt: Instant
    ): MerchantSummary {
        return observe("transition") {
            val application = getApplication(applicationId)
            application.transitionTo(nextStatus, changedAt)
            val saved = merchantApplicationRepository.save(application)
            audit(saved, SYSTEM_ACTOR, "transition", changedAt, mapOf("nextStatus" to nextStatus.name))
            saved
        }.toSummary()
    }

    override fun requestChanges(
        applicationId: UUID,
        reason: String,
        changedAt: Instant
    ): MerchantSummary {
        return observe("request_changes") {
            require(reason.isNotBlank()) { "Change request reason is required" }
            val application = getApplication(applicationId)
            application.requestChanges(reason, changedAt)
            val saved = merchantApplicationRepository.save(application)
            audit(saved, SYSTEM_ACTOR, "request_changes", changedAt, mapOf("reason" to reason))
            saved
        }.toSummary()
    }

    override fun recordDecision(
        applicationId: UUID,
        outcome: ReviewOutcome,
        reason: String,
        reviewerId: String,
        decidedAt: Instant
    ): MerchantSummary {
        return observe("record_decision") {
            require(reason.isNotBlank()) { "Decision reason is required" }
            require(reviewerId.isNotBlank()) { "Reviewer id is required" }
            val application = getApplication(applicationId)
            application.recordDecision(outcome, reason, reviewerId, decidedAt)
            val saved = merchantApplicationRepository.save(application)
            audit(saved, reviewerId, "record_decision", decidedAt, mapOf("outcome" to outcome.name))
            saved
        }.toSummary()
    }

    override fun startMonitoring(
        applicationId: UUID,
        triggerReason: String,
        changedAt: Instant
    ): MerchantSummary {
        return observe("start_monitoring") {
            require(triggerReason.isNotBlank()) { "Monitoring trigger reason is required" }
            val application = getApplication(applicationId)
            application.startMonitoring(
                MonitoringRecord(lastTriggerReason = triggerReason, lastReviewedAt = changedAt, createdAt = changedAt),
                changedAt
            )
            val saved = merchantApplicationRepository.save(application)
            audit(saved, SYSTEM_ACTOR, "start_monitoring", changedAt, mapOf("triggerReason" to triggerReason))
            saved
        }.toSummary()
    }

    @Transactional(readOnly = true)
    override fun findOrderableProducts(productIds: Collection<UUID>) =
        if (productIds.isEmpty()) {
            emptyList()
        } else {
            val products = merchantProductRepository.findAllById(productIds.toSet())
            val activeBranchIds = merchantBranchRepository.findAllById(products.mapNotNull { it.merchantBranchId }.toSet())
                .filter { it.active }
                .map { it.id }
                .toSet()

            products.map {
                com.elegant.software.blitzpay.merchant.api.OrderableMerchantProduct(
                    productId = it.id,
                    merchantApplicationId = it.merchantApplicationId,
                    branchId = it.merchantBranchId,
                    name = it.name,
                    description = it.description,
                    unitPrice = it.unitPrice,
                    active = it.active && (it.merchantBranchId == null || it.merchantBranchId in activeBranchIds),
                )
            }
        }

    @Transactional(readOnly = true)
    override fun requireActiveBranch(merchantId: UUID, branchId: UUID): ActiveBranchInfo {
        val merchant = merchantApplicationRepository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }
        if (merchant.merchantStatus != MerchantOperationalStatus.ACTIVE) {
            throw IllegalStateException("Merchant is inactive: $merchantId")
        }
        val branch = merchantBranchRepository.findById(branchId)
            .orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        if (branch.merchantApplicationId != merchantId) {
            throw IllegalStateException("Branch does not belong to merchant: $branchId")
        }
        if (!branch.active) {
            throw IllegalStateException("Branch is inactive: $branchId")
        }
        return ActiveBranchInfo(
            branchId = branch.id,
            merchantApplicationId = branch.merchantApplicationId,
            location = branch.location?.let {
                BranchLocationInfo(it.latitude, it.longitude, it.geofenceRadiusMeters.toDouble())
            },
        )
    }

    @Transactional(readOnly = true)
    override fun getEnabledOfferings(merchantId: UUID): Set<String> =
        merchantOfferingAssignmentRepository.findAllByMerchantApplicationId(merchantId)
            .map { it.offeringCode }
            .toSet()

    private fun getApplication(applicationId: UUID): MerchantApplication =
        merchantApplicationRepository.findById(applicationId)
            .orElseThrow { NoSuchElementException("Merchant application not found: $applicationId") }

    private fun ensureNoDuplicateActiveApplication(application: MerchantApplication) {
        if (!ACTIVE_APPLICATION_STATUSES.contains(application.status)) {
            val duplicateExists = merchantApplicationRepository.existsByBusinessProfileRegistrationNumberAndStatusIn(
                application.businessProfile.registrationNumber,
                ACTIVE_APPLICATION_STATUSES
            )
            require(!duplicateExists) {
                "An active merchant application already exists for registration number ${application.businessProfile.registrationNumber}"
            }
        }
    }

    private fun audit(
        application: MerchantApplication,
        actorId: String,
        action: String,
        occurredAt: Instant,
        details: Map<String, String> = emptyMap()
    ) {
        merchantAuditTrail.record(
            MerchantAuditEvent(
                applicationId = application.id,
                applicationReference = application.applicationReference,
                actorId = actorId,
                action = action,
                status = application.status,
                occurredAt = occurredAt,
                details = details
            )
        )
    }

    private fun observe(action: String, block: () -> MerchantApplication): MerchantApplication =
        try {
            block().also { merchantObservabilitySupport.recordSuccess(action, it.status) }
        } catch (ex: RuntimeException) {
            merchantObservabilitySupport.recordFailure(action, ex::class.simpleName ?: "runtime_exception")
            throw ex
        }

    private fun MerchantApplication.toSummary() = MerchantSummary(
        applicationId = id,
        applicationReference = applicationReference,
        merchantCode = merchantCode,
        merchantName = merchantName.ifBlank { businessProfile.legalBusinessName },
        registrationNumber = businessProfile.registrationNumber,
        status = status,
        submittedAt = submittedAt,
        lastUpdatedAt = lastUpdatedAt
    )

    companion object {
        private const val SYSTEM_ACTOR = "system"
        private val ACTIVE_APPLICATION_STATUSES = setOf(
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
