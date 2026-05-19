package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.ProductCategoryResponse
import com.elegant.software.blitzpay.merchant.api.RenameProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.UpdateProductCategoryDurationRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantProductCategory
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class MerchantProductCategoryService(
    private val categoryRepository: MerchantProductCategoryRepository,
    private val productRepository: MerchantProductRepository,
    private val merchantApplicationRepository: MerchantApplicationRepository,
) {
    private val log = LoggerFactory.getLogger(MerchantProductCategoryService::class.java)

    fun create(merchantId: UUID, request: CreateProductCategoryRequest): ProductCategoryResponse {
        requireMerchantExists(merchantId)
        val normalizedName = normalizeName(request.name)
        require(
            categoryRepository.findByMerchantApplicationIdAndNameIgnoreCase(merchantId, normalizedName) == null
        ) {
            "A category named '$normalizedName' already exists for this merchant"
        }

        val saved = categoryRepository.save(
            MerchantProductCategory(
                merchantApplicationId = merchantId,
                name = normalizedName,
                estimatedDurationMinutes = request.estimatedDurationMinutes
            )
        )
        log.info("Product category created: id={} merchant={}", saved.id, merchantId)
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun list(merchantId: UUID): List<ProductCategoryResponse> {
        requireMerchantExists(merchantId)
        return categoryRepository.findAllByMerchantApplicationId(merchantId)
            .sortedBy { it.name.lowercase() }
            .map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun findByName(merchantId: UUID, categoryName: String): ProductCategoryResponse? {
        requireMerchantExists(merchantId)
        return categoryRepository.findByMerchantApplicationIdAndNameIgnoreCase(merchantId, normalizeName(categoryName))
            ?.toResponse()
    }

    fun rename(merchantId: UUID, categoryId: UUID, request: RenameProductCategoryRequest): ProductCategoryResponse {
        requireMerchantExists(merchantId)
        val category = categoryRepository.findByMerchantApplicationIdAndId(merchantId, categoryId)
            ?: throw NoSuchElementException("Category not found: $categoryId")
        val normalizedName = normalizeName(request.name)
        val duplicate = categoryRepository.findByMerchantApplicationIdAndNameIgnoreCase(merchantId, normalizedName)
        require(duplicate == null || duplicate.id == category.id) {
            "A category named '$normalizedName' already exists for this merchant"
        }

        category.rename(normalizedName)
        val saved = categoryRepository.save(category)
        log.info("Product category renamed: id={} merchant={}", categoryId, merchantId)
        return saved.toResponse()
    }

    fun updateDuration(merchantId: UUID, categoryId: UUID, request: UpdateProductCategoryDurationRequest): ProductCategoryResponse {
        requireMerchantExists(merchantId)
        val category = categoryRepository.findByMerchantApplicationIdAndId(merchantId, categoryId)
            ?: throw NoSuchElementException("Category not found: $categoryId")

        category.updateDuration(request.estimatedDurationMinutes)
        val saved = categoryRepository.save(category)
        log.info("Product category duration updated: id={} merchant={} duration={}", categoryId, merchantId, request.estimatedDurationMinutes)
        return saved.toResponse()
    }

    fun delete(merchantId: UUID, categoryId: UUID) {
        requireMerchantExists(merchantId)
        val category = categoryRepository.findByMerchantApplicationIdAndId(merchantId, categoryId)
            ?: throw NoSuchElementException("Category not found: $categoryId")
        val assignedProducts = productRepository.countByProductCategoryIdAndActiveTrue(categoryId)
        check(assignedProducts == 0L) {
            "Cannot delete category '${category.name}': $assignedProducts product(s) are still assigned to it"
        }
        categoryRepository.deleteById(categoryId)
        log.info("Product category deleted: id={} merchant={}", categoryId, merchantId)
    }

    private fun requireMerchantExists(merchantId: UUID) {
        require(merchantApplicationRepository.existsById(merchantId)) {
            "Merchant not found: $merchantId"
        }
    }

    private fun normalizeName(name: String): String {
        require(name.isNotBlank()) { "Category name must not be blank" }
        val normalized = name.trim()
        require(normalized.length <= 100) { "Category name must be <= 100 characters" }
        return normalized
    }

    private fun MerchantProductCategory.toResponse() = ProductCategoryResponse(
        id = id,
        name = name,
        estimatedDurationMinutes = estimatedDurationMinutes,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
