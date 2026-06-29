package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import org.springframework.stereotype.Component

@Component
class MerchantAccessPolicy {

    fun requireReadAccess(actor: MerchantActor, merchant: Merchant) {
        require(canRead(actor, merchant)) {
            "Actor ${actor.actorId} is not allowed to read merchant ${merchant.merchantCode}"
        }
    }

    fun requireReviewAccess(actor: MerchantActor) {
        require(actor.role in setOf(MerchantActorRole.OPERATIONS_REVIEWER, MerchantActorRole.COMPLIANCE_REVIEWER, MerchantActorRole.SYSTEM)) {
            "Actor ${actor.actorId} is not allowed to perform review actions"
        }
    }

    private fun canRead(actor: MerchantActor, merchant: Merchant): Boolean =
        when (actor.role) {
            MerchantActorRole.MERCHANT_APPLICANT ->
                actor.merchantApplicationReference == merchant.merchantCode
            MerchantActorRole.OPERATIONS_REVIEWER,
            MerchantActorRole.COMPLIANCE_REVIEWER,
            MerchantActorRole.SYSTEM -> true
        }
}
