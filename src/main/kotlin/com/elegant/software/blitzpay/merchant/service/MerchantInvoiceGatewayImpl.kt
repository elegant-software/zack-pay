package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceGateway
import com.elegant.software.blitzpay.merchant.api.MerchantInvoiceProfile
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MerchantInvoiceGatewayImpl(
    private val repository: MerchantRepository,
) : MerchantInvoiceGateway {

    override fun findForInvoice(merchantId: UUID): MerchantInvoiceProfile? {
        val merchant = repository.findById(merchantId).orElse(null) ?: return null
        return MerchantInvoiceProfile(
            merchantName = merchant.merchantName,
            registrationNumber = "",
            addressLine1 = merchant.address?.addressLine1,
            city = merchant.address?.city,
            postalCode = merchant.address?.postalCode,
            country = merchant.address?.country ?: merchant.operatingCountry,
            defaultVatPercent = merchant.defaultVatPercent,
            logoStorageKey = merchant.logoStorageKey,
        )
    }
}
