package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.ProductCategoryResponse
import com.elegant.software.blitzpay.merchant.api.UpdateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategory
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductCategoryTranslationRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class MerchantProductCategoryService(
    private val categoryRepository: MerchantProductCategoryRepository,
    private val productRepository: MerchantProductRepository,
    private val merchantRepository: MerchantRepository,
    private val offeringAssignmentRepository: MerchantOfferingAssignmentRepository,
    private val categoryTranslationRepository: MerchantProductCategoryTranslationRepository,
    private val catalogTranslationService: CatalogTranslationService,
    private val menuLanguageService: MenuLanguageService,
) {
    private val log = LoggerFactory.getLogger(MerchantProductCategoryService::class.java)

    fun create(merchantId: UUID, request: CreateProductCategoryRequest): ProductCategoryResponse {
        requireMerchantExists(merchantId)
        val normalizedName = normalizeName(request.name)
        require(
            categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, normalizedName) == null
        ) {
            "A category named '$normalizedName' already exists for this merchant"
        }

        if (request.estimatedDurationMinutes != null) requireAppointmentBookingEnabled(merchantId)
        val saved = categoryRepository.save(
            MerchantProductCategory(
                merchantId = merchantId,
                name = normalizedName,
                estimatedDurationMinutes = request.estimatedDurationMinutes
            )
        )
        log.info("Product category created: id={} merchant={}", saved.id, merchantId)
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun list(merchantId: UUID, language: String? = null): List<ProductCategoryResponse> {
        requireMerchantExists(merchantId)
        val categories = categoryRepository.findAllByMerchantId(merchantId)
        val original = menuLanguageService.originalLanguage(merchantId)
        val translatedNames = catalogTranslationService.categoryNames(categories.map { it.id }, language)
        return categories
            .map { category ->
                translatedNames[category.id]
                    ?.let { category.toResponse().copy(name = it, language = language) }
                    ?: category.toResponse().copy(language = original)
            }
            .sortedBy { it.name.lowercase() }
    }

    @Transactional(readOnly = true)
    fun findByName(merchantId: UUID, categoryName: String): ProductCategoryResponse? {
        requireMerchantExists(merchantId)
        return categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, normalizeName(categoryName))
            ?.toResponse()
    }

    fun update(merchantId: UUID, categoryId: UUID, request: UpdateProductCategoryRequest): ProductCategoryResponse {
        requireMerchantExists(merchantId)
        val category = categoryRepository.findByMerchantIdAndId(merchantId, categoryId)
            ?: throw NoSuchElementException("Category not found: $categoryId")
        val normalizedName = normalizeName(request.name)
        val duplicate = categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, normalizedName)
        require(duplicate == null || duplicate.id == category.id) {
            "A category named '$normalizedName' already exists for this merchant"
        }
        if (request.estimatedDurationMinutes != null) requireAppointmentBookingEnabled(merchantId)

        category.rename(normalizedName)
        category.updateDuration(request.estimatedDurationMinutes)
        val saved = categoryRepository.save(category)
        log.info("Product category updated: id={} merchant={}", categoryId, merchantId)
        return saved.toResponse()
    }

    fun delete(merchantId: UUID, categoryId: UUID) {
        requireMerchantExists(merchantId)
        val category = categoryRepository.findByMerchantIdAndId(merchantId, categoryId)
            ?: throw NoSuchElementException("Category not found: $categoryId")
        val assignedProducts = productRepository.countByProductCategoryIdAndActiveTrue(categoryId)
        check(assignedProducts == 0L) {
            "Cannot delete category '${category.name}': $assignedProducts product(s) are still assigned to it"
        }
        categoryTranslationRepository.deleteAllByCategoryId(categoryId)
        categoryRepository.deleteById(categoryId)
        log.info("Product category deleted: id={} merchant={}", categoryId, merchantId)
    }

    private fun requireAppointmentBookingEnabled(merchantId: UUID) {
        require(offeringAssignmentRepository.existsByMerchantIdAndOfferingCode(merchantId, "APPOINTMENT_BOOKING")) {
            "Estimated service duration requires Appointment Booking to be enabled for this merchant"
        }
    }

    private fun requireMerchantExists(merchantId: UUID) {
        require(merchantRepository.existsById(merchantId)) {
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
