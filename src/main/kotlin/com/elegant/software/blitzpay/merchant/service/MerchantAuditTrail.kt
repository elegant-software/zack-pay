package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOnboardingStatus
import java.time.Instant
import java.util.UUID

interface MerchantAuditTrail {
    fun record(event: MerchantAuditEvent)
}

data class MerchantAuditEvent(
    val applicationId: UUID,
    val applicationReference: String,
    val actorId: String,
    val action: String,
    val status: MerchantOnboardingStatus,
    val occurredAt: Instant,
    val details: Map<String, String> = emptyMap()
)
