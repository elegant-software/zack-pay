package com.elegant.software.blitzpay.merchant.persistence.model

/**
 * Canonical lifecycle for merchant onboarding.
 *
 * Transitions are intentionally centralized here so later application and
 * persistence layers do not duplicate or drift from the approved workflow.
 */
enum class MerchantOnboardingStatus {
    DRAFT,
    SUBMITTED,
    VERIFICATION,
    SCREENING,
    RISK_REVIEW,
    DECISION_PENDING,
    SETUP,
    ACTIVE,
    MONITORING,
    ACTION_REQUIRED,
    REJECTED
}

object MerchantOnboardingLifecycle {
    private val transitions = mapOf(
        MerchantOnboardingStatus.DRAFT to setOf(
            MerchantOnboardingStatus.SUBMITTED,
            MerchantOnboardingStatus.ACTIVE
        ),
        MerchantOnboardingStatus.SUBMITTED to setOf(
            MerchantOnboardingStatus.VERIFICATION,
            MerchantOnboardingStatus.ACTION_REQUIRED,
            MerchantOnboardingStatus.REJECTED
        ),
        MerchantOnboardingStatus.VERIFICATION to setOf(
            MerchantOnboardingStatus.SCREENING,
            MerchantOnboardingStatus.ACTION_REQUIRED,
            MerchantOnboardingStatus.REJECTED
        ),
        MerchantOnboardingStatus.SCREENING to setOf(
            MerchantOnboardingStatus.RISK_REVIEW,
            MerchantOnboardingStatus.ACTION_REQUIRED,
            MerchantOnboardingStatus.REJECTED
        ),
        MerchantOnboardingStatus.RISK_REVIEW to setOf(
            MerchantOnboardingStatus.DECISION_PENDING,
            MerchantOnboardingStatus.ACTION_REQUIRED,
            MerchantOnboardingStatus.REJECTED
        ),
        MerchantOnboardingStatus.DECISION_PENDING to setOf(
            MerchantOnboardingStatus.SETUP,
            MerchantOnboardingStatus.ACTION_REQUIRED,
            MerchantOnboardingStatus.REJECTED
        ),
        MerchantOnboardingStatus.SETUP to setOf(
            MerchantOnboardingStatus.ACTIVE,
            MerchantOnboardingStatus.ACTION_REQUIRED,
            MerchantOnboardingStatus.REJECTED
        ),
        MerchantOnboardingStatus.ACTIVE to setOf(
            MerchantOnboardingStatus.MONITORING,
            MerchantOnboardingStatus.ACTION_REQUIRED,
            MerchantOnboardingStatus.REJECTED
        ),
        MerchantOnboardingStatus.MONITORING to setOf(
            MerchantOnboardingStatus.ACTION_REQUIRED,
            MerchantOnboardingStatus.REJECTED
        ),
        MerchantOnboardingStatus.ACTION_REQUIRED to setOf(
            MerchantOnboardingStatus.SUBMITTED,
            MerchantOnboardingStatus.VERIFICATION,
            MerchantOnboardingStatus.SCREENING,
            MerchantOnboardingStatus.RISK_REVIEW,
            MerchantOnboardingStatus.DECISION_PENDING,
            MerchantOnboardingStatus.SETUP,
            MerchantOnboardingStatus.MONITORING,
            MerchantOnboardingStatus.REJECTED
        ),
        MerchantOnboardingStatus.REJECTED to emptySet()
    )

    fun allowedNextStatuses(from: MerchantOnboardingStatus): Set<MerchantOnboardingStatus> =
        transitions.getValue(from)

    fun canTransition(
        from: MerchantOnboardingStatus,
        to: MerchantOnboardingStatus
    ): Boolean = to in allowedNextStatuses(from)

    fun requireTransition(
        from: MerchantOnboardingStatus,
        to: MerchantOnboardingStatus
    ) {
        require(canTransition(from, to)) {
            "Invalid merchant onboarding transition: $from -> $to"
        }
    }
}
