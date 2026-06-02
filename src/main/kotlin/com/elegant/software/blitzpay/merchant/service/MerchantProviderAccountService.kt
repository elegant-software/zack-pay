package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.ProviderAccountResponse
import com.elegant.software.blitzpay.merchant.domain.MerchantProviderAccount
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProviderAccountRepository
import com.elegant.software.blitzpay.merchant.repository.PaymentServiceProviderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class MerchantProviderAccountService(
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val providerAccountRepository: MerchantProviderAccountRepository,
    private val pspRepository: PaymentServiceProviderRepository,
) {

    fun list(merchantId: UUID): List<ProviderAccountResponse> {
        requireMerchantExists(merchantId)
        return providerAccountRepository
            .findAllByMerchantApplicationId(merchantId)
            .map { it.toResponse() }
    }

    fun enable(merchantId: UUID, pspCode: String): ProviderAccountResponse {
        requireMerchantExists(merchantId)
        requireValidPsp(pspCode)
        val account = providerAccountRepository
            .findByMerchantApplicationIdAndPspCode(merchantId, pspCode)
            ?: MerchantProviderAccount(
                merchantApplicationId = merchantId,
                pspCode = pspCode,
                enabled = true,
            )
        account.enabled = true
        account.updatedAt = Instant.now()
        return providerAccountRepository.save(account).toResponse()
    }

    fun disable(merchantId: UUID, pspCode: String) {
        requireMerchantExists(merchantId)
        val account = providerAccountRepository
            .findByMerchantApplicationIdAndPspCode(merchantId, pspCode)
            ?: throw NoSuchElementException("Provider account not found for merchant $merchantId and PSP $pspCode")
        account.enabled = false
        account.updatedAt = Instant.now()
        providerAccountRepository.save(account)
    }

    private fun requireMerchantExists(merchantId: UUID) {
        if (!merchantApplicationRepository.existsById(merchantId)) {
            throw NoSuchElementException("Merchant not found: $merchantId")
        }
    }

    private fun requireValidPsp(pspCode: String) {
        if (!pspRepository.existsById(pspCode)) {
            throw IllegalArgumentException("Unknown PSP code: $pspCode. Valid codes: STRIPE, TRUELAYER, PAYPAL")
        }
    }

    private fun MerchantProviderAccount.toResponse() = ProviderAccountResponse(
        id = id,
        pspCode = pspCode,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
