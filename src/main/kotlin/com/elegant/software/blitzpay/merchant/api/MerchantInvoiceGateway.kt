package com.elegant.software.blitzpay.merchant.api

import org.springframework.modulith.NamedInterface
import java.math.BigDecimal
import java.util.UUID

@NamedInterface("MerchantInvoiceGateway")
interface MerchantInvoiceGateway {
    fun findForInvoice(merchantId: UUID): MerchantInvoiceProfile?
}

data class MerchantInvoiceProfile(
    val merchantName: String,
    val registrationNumber: String,
    val addressLine1: String?,
    val city: String?,
    val postalCode: String?,
    val country: String?,
    val defaultVatPercent: BigDecimal?,
    val logoStorageKey: String?,
)
