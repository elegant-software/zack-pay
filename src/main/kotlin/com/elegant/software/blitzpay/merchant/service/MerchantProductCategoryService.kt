package com.elegant.software.blitzpay.merchant.service

import org.springframework.http.HttpStatus
import com.elegant.software.blitzpay.config.ApiException
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
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import com.elegant.software.blitzpay.storage.StorageService
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
    private val storageService: StorageService,
) {
    private val log = LoggerFactory.getLogger(MerchantProductCategoryService::class.java)

    fun create(merchantId: UUID, request: CreateProductCategoryRequest): ProductCategoryResponse =
        create(merchantId, request, null)

    fun create(merchantId: UUID, request: CreateProductCategoryRequest, image: ProductImageUpload?): ProductCategoryResponse {
        requireMerchantExists(merchantId)
        val normalizedName = normalizeName(request.name)
        require(
            categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, normalizedName) == null
        ) {
            "A category named '$normalizedName' already exists for this merchant"
        }

        if (request.estimatedDurationMinutes != null) requireAppointmentBookingEnabled(merchantId)
        val saved = saveWithImage(
            MerchantProductCategory(
                merchantId = merchantId,
                name = normalizedName,
                estimatedDurationMinutes = request.estimatedDurationMinutes
            ), image, false
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

    fun update(merchantId: UUID, categoryId: UUID, request: UpdateProductCategoryRequest): ProductCategoryResponse =
        update(merchantId, categoryId, request, null, false)

    fun update(
        merchantId: UUID, categoryId: UUID, request: UpdateProductCategoryRequest,
        image: ProductImageUpload?, removeImage: Boolean = false,
    ): ProductCategoryResponse {
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
        val saved = saveWithImage(category, image, removeImage)
        log.info("Product category updated: id={} merchant={}", categoryId, merchantId)
        return saved.toResponse()
    }

    fun delete(merchantId: UUID, categoryId: UUID) {
        requireMerchantExists(merchantId)
        val category = categoryRepository.findByMerchantIdAndId(merchantId, categoryId)
            ?: throw NoSuchElementException("Category not found: $categoryId")
        val assignedProducts = productRepository.countByProductCategoryIdAndActiveTrue(categoryId)
        if (assignedProducts != 0L) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "Cannot delete category '${category.name}': $assignedProducts product(s) are still assigned to it",
            )
        }
        categoryTranslationRepository.deleteAllByCategoryId(categoryId)
        categoryRepository.deleteById(categoryId)
        cleanupAfterCommit(category.imageStorageKey)
        log.info("Product category deleted: id={} merchant={}", categoryId, merchantId)
    }

    private fun saveWithImage(
        category: MerchantProductCategory, image: ProductImageUpload?, removeImage: Boolean,
    ): MerchantProductCategory {
        require(image == null || !removeImage) { "Cannot upload and remove a category image in the same request" }
        val previousKey = category.imageStorageKey
        val newKey = image?.let {
            require(it.bytes.isNotEmpty()) { "Category image must not be empty" }
            val key = "merchants/${category.merchantId}/categories/${category.id}/${UUID.randomUUID()}.${it.extension}"
            storageService.upload(key, it.contentType, it.bytes)
            key
        }
        val transactional = TransactionSynchronizationManager.isSynchronizationActive()
        if (newKey != null && transactional) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) cleanup(newKey)
                }
            })
        }
        if (newKey != null || removeImage) category.imageStorageKey = newKey
        val saved = try {
            categoryRepository.save(category)
        } catch (ex: Exception) {
            if (!transactional) newKey?.let(::cleanup)
            throw ex
        }
        if (previousKey != category.imageStorageKey) cleanupAfterCommit(previousKey)
        return saved
    }

    private fun cleanupAfterCommit(key: String?) {
        if (key == null) return
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = cleanup(key)
            })
        } else cleanup(key)
    }

    private fun cleanup(key: String) {
        runCatching { storageService.delete(key) }
            .onFailure { log.warn("Failed to clean up category image key={}", key, it) }
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
        updatedAt = updatedAt,
        imageUrl = imageStorageKey?.let { key ->
            runCatching { storageService.presignDownload(key) }
                .onFailure { log.warn("Failed to sign category image key={}", key, it) }
                .getOrNull()
        },
    )
}
