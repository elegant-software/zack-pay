package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.PreparationConfiguration
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantPreparationConfiguration
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantPreparationConfigurationRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class PreparationConfigurationService(
    private val configurations: MerchantPreparationConfigurationRepository,
    private val categories: MerchantProductCategoryRepository,
    private val products: MerchantProductRepository,
) {
    @Transactional(readOnly = true)
    fun get(branchId: UUID): PreparationConfiguration =
        configurations.findById(branchId).orElse(null)?.configuration ?: PreparationConfiguration()

    fun save(merchantId: UUID, branchId: UUID, request: PreparationConfiguration): PreparationConfiguration {
        require(request.stations.size <= 50) { "A branch supports at most 50 preparation stations" }
        val normalized = request.copy(stations = request.stations.map { it.copy(name = it.name.trim()) })
        require(normalized.stations.all { it.name.isNotBlank() && it.name.length <= 100 }) {
            "Station names must contain between 1 and 100 characters"
        }
        val stationIds = normalized.stations.map { it.id }.toSet()
        require(UUID(0, 0) !in stationIds) { "The unassigned station identifier is reserved" }
        require(stationIds.size == normalized.stations.size) { "Station identifiers must be unique" }
        require(normalized.stations.map { it.name.lowercase() }.toSet().size == normalized.stations.size) {
            "Station names must be unique within the branch"
        }
        require((normalized.categoryDefaults.values + normalized.productOverrides.values).all { it in stationIds }) {
            "Every routing assignment must reference a station in this branch"
        }
        normalized.categoryDefaults.keys.forEach { id ->
            require(categories.findByMerchantIdAndId(merchantId, id) != null) { "Category does not belong to this merchant" }
        }
        normalized.productOverrides.keys.forEach { id ->
            require(products.findByIdAndMerchantBranchId(id, branchId).orElse(null)?.merchantId == merchantId) {
                "Product does not belong to this branch"
            }
        }
        val entity = configurations.findById(branchId).orElse(null) ?: MerchantPreparationConfiguration(branchId)
        entity.configuration = normalized
        return configurations.save(entity).configuration
    }
}
