package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantApplication
import com.elegant.software.blitzpay.merchant.persistence.model.PersonRole
import com.elegant.software.blitzpay.merchant.persistence.model.SupportingMaterialType
import org.springframework.stereotype.Component

@Component
class MerchantApplicationValidator {

    fun validateForSubmission(application: MerchantApplication) {
        require(application.businessProfile.legalBusinessName.isNotBlank()) {
            "Legal business name is required"
        }
        require(application.businessProfile.registrationNumber.isNotBlank()) {
            "Business registration number is required"
        }
        require(application.primaryContact.fullName.isNotBlank()) {
            "Primary contact name is required"
        }
        require(application.primaryContact.email.isNotBlank()) {
            "Primary contact email is required"
        }
        require(application.people.any { it.role == PersonRole.BENEFICIAL_OWNER }) {
            "At least one beneficial owner is required"
        }
        require(
            application.supportingMaterials.any { it.type == SupportingMaterialType.BUSINESS_REGISTRATION }
        ) {
            "Business registration document is required"
        }
    }
}
