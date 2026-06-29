package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.ActiveBranchInfo
import com.elegant.software.blitzpay.merchant.api.BranchLocationInfo
import com.elegant.software.blitzpay.merchant.api.MerchantGateway
import com.elegant.software.blitzpay.merchant.api.OrderableMerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class MerchantOnboardingService(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
    private val merchantOfferingAssignmentRepository: MerchantOfferingAssignmentRepository,
) : MerchantGateway {

    @Transactional(readOnly = true)
    override fun findOrderableProducts(productIds: Collection<UUID>) =
        if (productIds.isEmpty()) {
            emptyList()
        } else {
            val products = merchantProductRepository.findAllById(productIds.toSet())
            val activeBranchIds = merchantBranchRepository.findAllById(products.mapNotNull { it.merchantBranchId }.toSet())
                .filter { it.active }
                .map { it.id }
                .toSet()

            products.map {
                OrderableMerchantProduct(
                    productId = it.id,
                    merchantId = it.merchantId,
                    branchId = it.merchantBranchId,
                    name = it.name,
                    description = it.description,
                    unitPrice = it.unitPrice,
                    active = it.active && (it.merchantBranchId == null || it.merchantBranchId in activeBranchIds),
                )
            }
        }

    @Transactional(readOnly = true)
    override fun requireActiveBranch(merchantId: UUID, branchId: UUID): ActiveBranchInfo {
        val merchant = merchantRepository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }
        if (merchant.merchantStatus != MerchantOperationalStatus.ACTIVE) {
            throw IllegalStateException("Merchant is inactive: $merchantId")
        }
        val branch = merchantBranchRepository.findById(branchId)
            .orElseThrow { NoSuchElementException("Branch not found: $branchId") }
        if (branch.merchantId != merchantId) {
            throw IllegalStateException("Branch does not belong to merchant: $branchId")
        }
        if (!branch.active) {
            throw IllegalStateException("Branch is inactive: $branchId")
        }
        return ActiveBranchInfo(
            branchId = branch.id,
            merchantId = branch.merchantId,
            location = branch.location?.let {
                BranchLocationInfo(it.latitude, it.longitude, it.geofenceRadiusMeters.toDouble())
            },
        )
    }

    @Transactional(readOnly = true)
    override fun getEnabledOfferings(merchantId: UUID): Set<String> =
        merchantOfferingAssignmentRepository.findAllByMerchantId(merchantId)
            .map { it.offeringCode }
            .toSet()
}
