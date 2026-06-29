package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MerchantLogoUploadResponse
import com.elegant.software.blitzpay.merchant.api.MerchantSummary
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MerchantLogoService(
    private val repository: MerchantRepository,
    private val storageService: StorageService,
) {

    fun createUploadUrl(merchantId: UUID, contentType: String): MerchantLogoUploadResponse {
        val merchant = repository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }

        val storageKey = MerchantLogoPolicy.storageKeyFor(merchant.id, contentType)
        val presigned = storageService.presignUpload(storageKey, contentType)

        return MerchantLogoUploadResponse(
            storageKey = presigned.storageKey,
            uploadUrl = presigned.uploadUrl,
            expiresAt = presigned.expiresAt,
        )
    }

    fun attachLogo(merchantId: UUID, storageKey: String): MerchantSummary {
        MerchantLogoPolicy.validateStorageKey(merchantId, storageKey)

        val merchant = repository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }

        merchant.updateLogo(storageKey)
        repository.save(merchant)

        return MerchantSummary(
            merchantId = merchant.id,
            merchantCode = merchant.merchantCode,
            merchantName = merchant.merchantName,
            merchantStatus = merchant.merchantStatus,
            lastUpdatedAt = merchant.lastUpdatedAt,
            logoStorageKey = merchant.logoStorageKey,
        )
    }
}
