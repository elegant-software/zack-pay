package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantApplication
import org.springframework.stereotype.Component

@Component
class MerchantAccessPolicy {

    fun requireReadAccess(actor: MerchantActor, application: MerchantApplication) {
        require(canRead(actor, application)) {
            "Actor ${actor.actorId} is not allowed to read application ${application.applicationReference}"
        }
    }

    fun requireReviewAccess(actor: MerchantActor) {
        require(actor.role in setOf(MerchantActorRole.OPERATIONS_REVIEWER, MerchantActorRole.COMPLIANCE_REVIEWER, MerchantActorRole.SYSTEM)) {
            "Actor ${actor.actorId} is not allowed to perform review actions"
        }
    }

    private fun canRead(actor: MerchantActor, application: MerchantApplication): Boolean =
        when (actor.role) {
            MerchantActorRole.MERCHANT_APPLICANT ->
                actor.merchantApplicationReference == application.applicationReference
            MerchantActorRole.OPERATIONS_REVIEWER,
            MerchantActorRole.COMPLIANCE_REVIEWER,
            MerchantActorRole.SYSTEM -> true
        }
}
