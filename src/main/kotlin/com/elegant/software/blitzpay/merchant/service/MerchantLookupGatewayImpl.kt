package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.BranchReference
import com.elegant.software.blitzpay.merchant.api.MerchantLookupGateway
import com.elegant.software.blitzpay.merchant.api.MerchantReference
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
@Transactional(readOnly = true)
class MerchantLookupGatewayImpl(
    private val merchantRepo: MerchantRepository,
    private val branchRepo: MerchantBranchRepository,
) : MerchantLookupGateway {

    override fun requireMerchant(merchantId: UUID): MerchantReference {
        val merchant = merchantRepo.findById(merchantId)
            .orElseThrow { IllegalArgumentException("merchant not found: $merchantId") }
        return MerchantReference(merchant.id, merchant.merchantName)
    }

    override fun findMerchant(merchantId: UUID): MerchantReference? =
        merchantRepo.findById(merchantId).map { MerchantReference(it.id, it.merchantName) }.orElse(null)

    override fun findBranch(branchId: UUID): BranchReference? =
        branchRepo.findById(branchId).map { BranchReference(it.id, it.merchantId, it.name) }.orElse(null)
}
