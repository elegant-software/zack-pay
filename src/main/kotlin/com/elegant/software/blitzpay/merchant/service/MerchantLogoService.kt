package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MerchantLogoUploadResponse
import com.elegant.software.blitzpay.merchant.api.MerchantSummary
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.storage.StorageService
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class MerchantLogoService(
    private val repository: MerchantApplicationRepository,
    private val storageService: StorageService,
) {

    fun createUploadUrl(merchantId: UUID, contentType: String): MerchantLogoUploadResponse {
        val application = repository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant application not found: $merchantId") }

        val storageKey = MerchantLogoPolicy.storageKeyFor(application.id, contentType)
        val presigned = storageService.presignUpload(storageKey, contentType)

        return MerchantLogoUploadResponse(
            storageKey = presigned.storageKey,
            uploadUrl = presigned.uploadUrl,
            expiresAt = presigned.expiresAt,
        )
    }

    fun attachLogo(merchantId: UUID, storageKey: String): MerchantSummary {
        MerchantLogoPolicy.validateStorageKey(merchantId, storageKey)

        val application = repository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant application not found: $merchantId") }

        application.updateLogo(storageKey)
        repository.save(application)

        return MerchantSummary(
            applicationId = application.id,
            applicationReference = application.applicationReference,
            merchantCode = application.merchantCode,
            merchantName = application.merchantName.ifBlank { application.businessProfile.legalBusinessName },
            registrationNumber = application.businessProfile.registrationNumber,
            status = application.status,
            submittedAt = application.submittedAt,
            lastUpdatedAt = application.lastUpdatedAt,
            logoStorageKey = application.businessProfile.logoStorageKey
        )
    }
}
