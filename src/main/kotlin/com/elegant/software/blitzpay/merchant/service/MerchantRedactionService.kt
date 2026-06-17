package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantApplication
import org.springframework.stereotype.Component

@Component
class MerchantRedactionService {

    fun redact(application: MerchantApplication): RedactedMerchantApplication = RedactedMerchantApplication(
        applicationReference = application.applicationReference,
        legalBusinessName = application.businessProfile.legalBusinessName,
        registrationNumber = maskRegistrationNumber(application.businessProfile.registrationNumber),
        primaryContactName = application.primaryContact.fullName,
        primaryContactEmail = maskEmail(application.primaryContact.email),
        primaryContactPhone = maskPhone(application.primaryContact.phoneNumber),
        peopleCount = application.people.size,
        supportingMaterialCount = application.supportingMaterials.size,
        status = application.status.name
    )

    private fun maskEmail(email: String): String {
        val parts = email.split("@", limit = 2)
        if (parts.size != 2) return "***"
        val local = parts[0]
        val visible = local.take(1)
        return "$visible***@${parts[1]}"
    }

    private fun maskPhone(phone: String): String =
        if (phone.length <= 4) "****" else "${"*".repeat(phone.length - 4)}${phone.takeLast(4)}"

    private fun maskRegistrationNumber(registrationNumber: String): String =
        if (registrationNumber.length <= 3) "***" else "${registrationNumber.take(3)}***"
}

data class RedactedMerchantApplication(
    val applicationReference: String,
    val legalBusinessName: String,
    val registrationNumber: String,
    val primaryContactName: String,
    val primaryContactEmail: String,
    val primaryContactPhone: String,
    val peopleCount: Int,
    val supportingMaterialCount: Int,
    val status: String
)
