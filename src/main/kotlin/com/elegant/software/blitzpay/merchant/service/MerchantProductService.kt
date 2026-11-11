package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.ProductResponse
import com.elegant.software.blitzpay.merchant.api.UpdateProductRequest
import com.elegant.software.blitzpay.merchant.api.CatalogProductPage
import com.elegant.software.blitzpay.merchant.api.BranchCatalogCategories
import com.elegant.software.blitzpay.merchant.api.BranchCatalogCategory
import com.elegant.software.blitzpay.merchant.api.CatalogThumbnailBatch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantEntityStatus
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductTranslation
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.storage.StorageService
import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.util.UUID

@Service
@Transactional
class MerchantProductService(
    private val productRepository: MerchantProductRepository,
    private val categoryRepository: MerchantProductCategoryRepository,
    private val merchantRepository: MerchantRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
    private val entityManager: EntityManager,
    private val storageService: StorageService,
    private val catalogTranslationService: CatalogTranslationService,
    private val menuLanguageService: MenuLanguageService,
    private val productImageProcessor: ProductImageProcessor,
) {
    private val log = LoggerFactory.getLogger(MerchantProductService::class.java)

    fun create(
        merchantId: UUID,
        request: CreateProductRequest,
        image: ProductImageUpload? = null,
        active: Boolean = true
    ): ProductResponse {
        requireMerchantExists(merchantId)
        validateProductFields(request.name, request.description, request.unitPrice)
        validateBranch(merchantId, request.branchId)
        val categoryId = validateCategory(merchantId, request.categoryId)
        val resolvedProductCode = resolveProductCode(request.branchId, request.productCode)
        val existingByCode = request.productCode?.let {
            productRepository.findByMerchantBranchIdAndProductCode(request.branchId, it)
        }

        if (existingByCode != null) {
            return updateExistingProduct(
                merchantId = merchantId,
                product = existingByCode,
                request = UpdateProductRequest(
                    name = request.name,
                    branchId = request.branchId,
                    unitPrice = request.unitPrice,
                    description = request.description,
                    categoryId = categoryId,
                    productCode = resolvedProductCode
                ),
                image = image
            )
        }

        val productId = UUID.randomUUID()
        val images = image?.let { uploadImage(merchantId, productId, it, request.branchId) }
        val imageStorageKey = images?.imageKey
        val product = MerchantProduct(
            id = productId,
            merchantId = merchantId,
            name = request.name.trim(),
            description = ProductImagePolicy.normalizeDescription(request.description),
            unitPrice = request.unitPrice,
            imageStorageKey = imageStorageKey,
            active = active,
            merchantBranchId = request.branchId,
            productCategoryId = categoryId,
            productCode = resolvedProductCode,
            thumbnailStorageKey = images?.thumbnailKey,
        )
        val saved = try {
            productRepository.save(product)
        } catch (ex: RuntimeException) {
            imageStorageKey?.let { cleanupUploadedImage(it) }
            images?.thumbnailKey?.let { cleanupUploadedImage(it) }
            throw ex
        }
        log.info("Product created: id={} merchant={}", saved.id, merchantId)
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    /** [language] is a resolved translation language (see [MenuLanguageService.resolve]); null serves the original. */
    fun list(
        merchantId: UUID,
        merchantBranchId: UUID,
        categoryId: UUID? = null,
        language: String? = null,
    ): List<ProductResponse> {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val products = if (categoryId == null) {
            productRepository.findAllByMerchantBranchId(merchantBranchId)
        } else {
            validateCategory(merchantId, categoryId)
            productRepository.findAllByMerchantBranchIdAndProductCategoryId(merchantBranchId, categoryId)
        }
        return products.toResponses(merchantId, language)
    }

    @Transactional(readOnly = true)
    fun catalogPage(
        merchantId: UUID, branchId: UUID, categoryId: UUID?, uncategorized: Boolean,
        includeInactive: Boolean, limit: Int, cursor: String?, language: String?,
    ): CatalogProductPage {
        require(limit in 1..100) { "limit must be between 1 and 100" }
        require(!uncategorized || categoryId == null) { "categoryId and uncategorized cannot be combined" }
        requireCatalogBranch(merchantId, branchId, includeInactive)
        enableTenantFilter(merchantId)
        validateCategory(merchantId, categoryId)
        val scope = CatalogScope(merchantId, branchId, categoryId, uncategorized, includeInactive)
        val position = scope.position(cursor)
        val products = productRepository.findCatalogPage(
            merchantId, branchId, categoryId, uncategorized, includeInactive,
            position?.createdAt, position?.productId, PageRequest.of(0, limit + 1),
        )
        val hasMore = products.size > limit
        val page = products.take(limit)
        log.info("Catalog page read: merchant={} branch={} count={} hasMore={}", merchantId, branchId, page.size, hasMore)
        return CatalogProductPage(
            items = page.toResponses(merchantId, language),
            nextCursor = if (hasMore) page.last().let { scope.cursor(it.createdAt, it.id) } else null,
            hasMore = hasMore, limit = limit,
        )
    }

    @Transactional(readOnly = true)
    fun catalogCategories(merchantId: UUID, branchId: UUID, includeInactive: Boolean, language: String?): BranchCatalogCategories {
        requireCatalogBranch(merchantId, branchId, includeInactive)
        enableTenantFilter(merchantId)
        val counts = productRepository.catalogCategoryCounts(merchantId, branchId, includeInactive)
        val countByCategory = counts.filter { it.categoryId != null }.associate { it.categoryId!! to it.productCount }
        val categories = categoryRepository.findAllById(countByCategory.keys).filter { it.merchantId == merchantId }
        val translations = catalogTranslationService.categoryNames(categories.map { it.id }, language)
        val original = menuLanguageService.originalLanguage(merchantId)
        val response = categories.map { category ->
            BranchCatalogCategory(
                id = category.id, name = translations[category.id] ?: category.name,
                productCount = countByCategory.getValue(category.id),
                estimatedDurationMinutes = category.estimatedDurationMinutes,
                language = if (translations.containsKey(category.id)) language else original,
                imageUrl = signedImageUrl(category.imageStorageKey),
            )
        }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
        return BranchCatalogCategories(
            categories = response,
            uncategorizedCount = counts.firstOrNull { it.categoryId == null }?.productCount ?: 0,
            totalCount = counts.sumOf { it.productCount },
        )
    }

    private fun requireCatalogBranch(merchantId: UUID, branchId: UUID, includeInactive: Boolean) {
        requireMerchantExists(merchantId)
        val exists = if (includeInactive) merchantBranchRepository.existsByMerchantIdAndId(merchantId, branchId)
            else merchantBranchRepository.existsByMerchantIdAndIdAndActiveTrue(merchantId, branchId)
        if (!exists) throw NoSuchElementException("Branch not found: $branchId")
    }

    fun backfillCatalogThumbnails(merchantId: UUID, branchId: UUID, limit: Int, cursor: String?): CatalogThumbnailBatch {
        require(limit in 1..24) { "limit must be between 1 and 24" }
        requireCatalogBranch(merchantId, branchId, true)
        enableTenantFilter(merchantId)
        val scope = CatalogScope(merchantId, branchId, null, false, true)
        val position = scope.position(cursor)
        val products = productRepository.findCatalogPage(
            merchantId, branchId, null, false, true, position?.createdAt, position?.productId, PageRequest.of(0, limit + 1),
        )
        var updated = 0
        var skipped = 0
        var failed = 0
        for (product in products.take(limit)) {
            val sourceKey = product.imageStorageKey
            val type = when (sourceKey?.substringAfterLast('.')?.lowercase(java.util.Locale.ROOT)) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                else -> null
            }
            if (sourceKey == null || type == null || product.thumbnailStorageKey != null) {
                skipped++
                continue
            }
            val thumbnail = try {
                productImageProcessor.thumbnail(ProductImageUpload(type, storageService.download(sourceKey)))
            } catch (ex: RuntimeException) {
                log.warn("Catalog thumbnail backfill failed: product={}", product.id, ex)
                failed++
                continue
            }
            if (thumbnail == null) {
                skipped++
                continue
            }
            val key = storeThumbnail(merchantId, branchId, product.id, thumbnail)
            if (key == null) {
                failed++
                continue
            }
            if (productRepository.attachCatalogThumbnail(product.id, merchantId, sourceKey, product.updatedAt, key) == 1) updated++
            else {
                cleanupUploadedImage(key)
                skipped++
            }
        }
        val hasMore = products.size > limit
        return CatalogThumbnailBatch(
            updated, skipped, failed,
            if (hasMore) products[limit - 1].let { scope.cursor(it.createdAt, it.id) } else null,
            hasMore,
        )
    }

    @Transactional(readOnly = true)
    fun get(merchantId: UUID, productId: UUID, merchantBranchId: UUID?, language: String? = null): ProductResponse {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val product = if (merchantBranchId != null) {
            productRepository.findByIdAndMerchantBranchId(productId, merchantBranchId)
                .orElseThrow { NoSuchElementException("Product not found: $productId") }
        } else {
            productRepository.findById(productId)
                .orElseThrow { NoSuchElementException("Product not found: $productId") }
                .also {
                    if (it.merchantId != merchantId) throw NoSuchElementException("Product not found: $productId")
                }
        }
        return listOf(product).toResponses(merchantId, language).single()
    }

    @Transactional(readOnly = true)
    fun findByName(merchantId: UUID, merchantBranchId: UUID, productName: String): ProductResponse? {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        return productRepository.findByNameAndMerchantIdAndMerchantBranchIdAndActiveTrue(
            productName,
            merchantId,
            merchantBranchId
        )?.toResponse()
    }

    @Transactional(readOnly = true)
    fun findByNameIncludingInactive(merchantId: UUID, merchantBranchId: UUID, productName: String): ProductResponse? {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        return productRepository.findByNameAndMerchantIdAndMerchantBranchId(
            productName,
            merchantId,
            merchantBranchId
        )?.toResponse()
    }

    @Transactional(readOnly = true)
    fun findByProductCode(merchantId: UUID, branchId: UUID, productCode: Long): ProductResponse? {
        requireMerchantExists(merchantId)
        return productRepository.findByMerchantBranchIdAndProductCode(branchId, productCode)
            ?.takeIf { it.merchantId == merchantId }
            ?.toResponse()
    }

    fun update(merchantId: UUID, productId: UUID, request: UpdateProductRequest, image: ProductImageUpload? = null): ProductResponse {
        requireMerchantExists(merchantId)
        validateProductFields(request.name, request.description, request.unitPrice)
        validateBranch(merchantId, request.branchId)

        enableTenantFilter(merchantId)
        val product = productRepository.findByIdAndMerchantBranchId(productId, request.branchId)
            .orElseThrow { NoSuchElementException("Product not found: $productId") }
        return updateResolvedProduct(merchantId, productId, request, image, product)
    }

    fun updateIncludingInactive(
        merchantId: UUID,
        productId: UUID,
        request: UpdateProductRequest,
        image: ProductImageUpload? = null
    ): ProductResponse {
        requireMerchantExists(merchantId)
        validateProductFields(request.name, request.description, request.unitPrice)
        validateBranch(merchantId, request.branchId)

        enableTenantFilter(merchantId)
        val product = productRepository.findById(productId)
            .orElseThrow { NoSuchElementException("Product not found: $productId") }
        return updateResolvedProduct(merchantId, productId, request, image, product)
    }

    private fun updateResolvedProduct(
        merchantId: UUID,
        productId: UUID,
        request: UpdateProductRequest,
        image: ProductImageUpload?,
        product: MerchantProduct
    ): ProductResponse {
        val categoryId = validateCategory(merchantId, request.categoryId)
        val targetProduct = request.productCode?.let { code ->
            productRepository.findByMerchantBranchIdAndProductCode(request.branchId, code)
                ?.takeUnless { it.id == product.id }
        } ?: product

        return updateExistingProduct(
            merchantId = merchantId,
            product = targetProduct,
            request = request.copy(categoryId = categoryId),
            image = image
        )
    }

    fun deactivate(merchantId: UUID, productId: UUID, merchantBranchId: UUID) {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val product = productRepository.findByIdAndActiveTrueAndMerchantBranchId(productId, merchantBranchId)
            .orElseThrow { NoSuchElementException("Product not found or already inactive: $productId") }

        product.deactivate()
        productRepository.save(product)
        log.info("Product deactivated: id={} merchant={}", productId, merchantId)
    }

    fun updateStatus(merchantId: UUID, productId: UUID, status: String): ProductResponse {
        require(status.isNotBlank()) { "status must not be blank" }
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val product = productRepository.findById(productId)
            .orElseThrow { NoSuchElementException("Product not found: $productId") }
        product.status = status
        product.updatedAt = java.time.Instant.now()
        return productRepository.save(product).toResponse()
    }

    fun markInactive(merchantId: UUID, productId: UUID): ProductResponse {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val product = productRepository.findById(productId)
            .orElseThrow { NoSuchElementException("Product not found: $productId") }
        product.active = false
        product.status = MerchantEntityStatus.INACTIVE
        product.updatedAt = java.time.Instant.now()
        return productRepository.save(product).toResponse()
    }

    private fun requireMerchantExists(merchantId: UUID) {
        require(merchantRepository.existsById(merchantId)) {
            "Merchant not found: $merchantId"
        }
    }

    private fun enableTenantFilter(merchantId: UUID) {
        val session = entityManager.unwrap(Session::class.java)
        session.enableFilter("tenantFilter").setParameter("merchantId", merchantId)
        entityManager.createNativeQuery("SELECT set_config('app.current_merchant_id', :mid, true)")
            .setParameter("mid", merchantId.toString())
            .singleResult
    }

    private fun validateProductFields(name: String, description: String?, unitPrice: BigDecimal) {
        require(name.isNotBlank()) { "Product name must not be blank" }
        ProductImagePolicy.normalizeDescription(description)
        require(unitPrice >= BigDecimal.ZERO) { "unitPrice must be >= 0" }
    }

    private fun validateBranch(merchantId: UUID, branchId: UUID) {
        require(merchantBranchRepository.existsByMerchantIdAndId(merchantId, branchId)) {
            "Merchant branch not found or does not belong to merchant: $branchId"
        }
    }

    private fun validateCategory(merchantId: UUID, categoryId: UUID?): UUID? {
        if (categoryId == null) return null
        require(categoryRepository.existsByIdAndMerchantId(categoryId, merchantId)) {
            "Category not found or does not belong to merchant: $categoryId"
        }
        return categoryId
    }

    private fun resolveProductCode(branchId: UUID, requestedProductCode: Long?): Long {
        requestedProductCode?.let {
            require(it > 0) { "productCode must be > 0" }
            return it
        }
        return (productRepository.findMaxProductCodeByMerchantBranchId(branchId) ?: 0L) + 1L
    }

    private fun updateExistingProduct(
        merchantId: UUID,
        product: MerchantProduct,
        request: UpdateProductRequest,
        image: ProductImageUpload?
    ): ProductResponse {
        val previousImageStorageKey = product.imageStorageKey
        val previousThumbnailKey = product.thumbnailStorageKey
        val images = image?.let { uploadImage(merchantId, product.id, it, request.branchId) }
        val newImageStorageKey = images?.imageKey ?: previousImageStorageKey
        val resolvedProductCode = request.productCode
            ?.let { resolveProductCode(request.branchId, it) }
            ?: product.productCode
            ?: resolveProductCode(request.branchId, null)
        product.update(
            name = request.name.trim(),
            description = ProductImagePolicy.normalizeDescription(request.description),
            unitPrice = request.unitPrice,
            imageStorageKey = newImageStorageKey,
            productCategoryId = request.categoryId,
            productCode = resolvedProductCode,
            active = request.active
        )
        if (request.branchId != product.merchantBranchId) {
            product.merchantBranchId = request.branchId
        }
        if (image != null) product.thumbnailStorageKey = images?.thumbnailKey
        val saved = try {
            productRepository.save(product)
        } catch (ex: RuntimeException) {
            if (newImageStorageKey != null && newImageStorageKey != previousImageStorageKey) {
                cleanupUploadedImage(newImageStorageKey)
            }
            images?.thumbnailKey?.takeIf { it != previousThumbnailKey }?.let { cleanupUploadedImage(it) }
            throw ex
        }
        log.info("Product updated: id={} merchant={}", saved.id, merchantId)
        if (image != null && previousThumbnailKey != product.thumbnailStorageKey) cleanupThumbnailAfterCommit(previousThumbnailKey)
        return saved.toResponse()
    }

    private data class UploadedImages(val imageKey: String, val thumbnailKey: String?)

    private fun uploadImage(merchantId: UUID, productId: UUID, image: ProductImageUpload, merchantBranchId: UUID): UploadedImages {
        val storageKey = "merchants/$merchantId/branches/$merchantBranchId/products/$productId/image.${image.extension}"
        val thumbnail = runCatching { productImageProcessor.thumbnail(image) }
            .onFailure { log.warn("Product thumbnail generation failed: product={}", productId, it) }.getOrNull()
        storageService.upload(storageKey, image.contentType, image.bytes)
        return UploadedImages(storageKey, thumbnail?.let { storeThumbnail(merchantId, merchantBranchId, productId, it) })
    }

    private fun storeThumbnail(merchantId: UUID, branchId: UUID, productId: UUID, thumbnail: ProductImageUpload): String? {
        val key = "merchants/$merchantId/branches/$branchId/products/$productId/thumbnail-${UUID.randomUUID()}.${thumbnail.extension}"
        val thumbnailKey = runCatching {
            storageService.upload(key, thumbnail.contentType, thumbnail.bytes)
            key
        }.onFailure { log.warn("Product thumbnail upload failed: product={}", productId, it) }.getOrNull()
        if (thumbnailKey != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) cleanupUploadedImage(thumbnailKey)
                }
            })
        }
        return thumbnailKey
    }

    private fun cleanupThumbnailAfterCommit(key: String?) {
        if (key == null) return
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = cleanupUploadedImage(key)
            })
        } else cleanupUploadedImage(key)
    }

    private fun cleanupUploadedImage(storageKey: String) {
        runCatching { storageService.delete(storageKey) }
            .onFailure { log.warn("Failed to clean up uploaded product image key={}", storageKey, it) }
    }

    private fun signedImageUrl(storageKey: String?): String? =
        storageKey?.let {
            runCatching { storageService.presignDownload(it) }
                .onFailure { ex -> log.warn("Failed to sign product image key={}", it, ex) }
                .getOrNull()
        }

    private fun List<MerchantProduct>.toResponses(merchantId: UUID, language: String?): List<ProductResponse> {
        val original = menuLanguageService.originalLanguage(merchantId)
        val translations = catalogTranslationService.productTexts(this, language)
        val categoryNames = catalogTranslationService.categoryNames(mapNotNull { it.productCategoryId }.toSet(), language)
        return map { product ->
            product.toResponse(
                translation = translations[product.id],
                translatedCategoryName = product.productCategoryId?.let { categoryNames[it] },
                originalLanguage = original,
            )
        }
    }

    private fun MerchantProduct.toResponse(
        translation: MerchantProductTranslation? = null,
        translatedCategoryName: String? = null,
        originalLanguage: String? = menuLanguageService.originalLanguage(merchantId),
    ) = ProductResponse(
        categoryId = productCategoryId,
        categoryName = translatedCategoryName ?: productCategoryId?.let {
            categoryRepository.findByMerchantIdAndId(merchantId, it)?.name
        },
        productCode = productCode,
        productId = id,
        branchId = merchantBranchId!!,
        name = translation?.name ?: name,
        description = if (translation != null) translation.description else description,
        unitPrice = unitPrice,
        imageUrl = signedImageUrl(imageStorageKey),
        thumbnailUrl = signedImageUrl(thumbnailStorageKey),
        active = active,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        language = translation?.language ?: originalLanguage,
    )
}
