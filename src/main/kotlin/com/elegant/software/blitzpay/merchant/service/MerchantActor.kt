package com.elegant.software.blitzpay.merchant.service

data class MerchantActor(
    val actorId: String,
    val role: MerchantActorRole,
    val merchantApplicationReference: String? = null
)

enum class MerchantActorRole {
    MERCHANT_APPLICANT,
    OPERATIONS_REVIEWER,
    COMPLIANCE_REVIEWER,
    SYSTEM
}
