package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import org.springframework.stereotype.Component

@Component
class MerchantRedactionService {

    fun redact(merchant: Merchant): RedactedMerchant = RedactedMerchant(
        merchantCode = merchant.merchantCode,
        merchantName = merchant.merchantName,
        contactEmail = merchant.publicEmail?.let { maskEmail(it) },
        contactPhone = merchant.publicPhoneNumber?.let { maskPhone(it) },
        merchantStatus = merchant.merchantStatus.name,
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
}

data class RedactedMerchant(
    val merchantCode: String,
    val merchantName: String,
    val contactEmail: String?,
    val contactPhone: String?,
    val merchantStatus: String,
)
