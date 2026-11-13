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
import com.elegant.software.blitzpay.storage.StorageService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneId
import java.util.UUID

@Service
@Transactional
class MerchantOnboardingService(
    private val merchantRepository: MerchantRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
    private val merchantOfferingAssignmentRepository: MerchantOfferingAssignmentRepository,
    private val preparationConfigurations: com.elegant.software.blitzpay.merchant.persistence.repository.MerchantPreparationConfigurationRepository,
    private val storageService: StorageService,
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

            val routing = products.mapNotNull { it.merchantBranchId }.distinct().associateWith {
                preparationConfigurations.findById(it).orElse(null)?.configuration
            }
            products.map {
                val config = routing[it.merchantBranchId]
                val stationId = config?.productOverrides?.get(it.id) ?: config?.categoryDefaults?.get(it.productCategoryId)
                // Unrouted items go to the default kitchen: the Branch's first station. No stations → whole-branch screen (UUID 0).
                val station = config?.stations?.find { candidate -> candidate.id == stationId } ?: config?.stations?.firstOrNull()

                OrderableMerchantProduct(
                    productId = it.id,
                    preparationStationId = station?.id ?: UUID(0, 0),
                    preparationStationName = station?.name ?: "Unassigned",
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
    override fun findPreparationStations(branchId: UUID) =
        preparationConfigurations.findById(branchId).orElse(null)?.configuration?.stations.orEmpty()

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

    @Transactional(readOnly = true)
    override fun findBranchTimezone(branchId: UUID): ZoneId? =
        merchantBranchRepository.findById(branchId).orElse(null)
            ?.timezone
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }

    @Transactional(readOnly = true)
    override fun findBranchLogoUrl(branchId: UUID): String? =
        merchantBranchRepository.findById(branchId).orElse(null)
            ?.let { merchantRepository.findById(it.merchantId).orElse(null) }
            ?.logoStorageKey
            ?.let { runCatching { storageService.presignDownload(it) }.getOrNull() }

    @Transactional(readOnly = true)
    override fun findBranchLanguage(branchId: UUID): String? =
        merchantBranchRepository.findById(branchId).orElse(null)
            ?.let { merchantRepository.findById(it.merchantId).orElse(null) }
            ?.originalMenuLanguage
}
