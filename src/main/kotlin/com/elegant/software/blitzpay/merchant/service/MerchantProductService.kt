package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.ProductResponse
import com.elegant.software.blitzpay.merchant.api.UpdateProductRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantEntityStatus
import com.elegant.software.blitzpay.merchant.domain.MerchantProduct
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductRepository
import com.elegant.software.blitzpay.storage.StorageService
import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

@Service
@Transactional
class MerchantProductService(
    private val productRepository: MerchantProductRepository,
    private val categoryRepository: MerchantProductCategoryRepository,
    private val merchantApplicationRepository: MerchantApplicationRepository,
    private val merchantBranchRepository: MerchantBranchRepository,
    private val entityManager: EntityManager,
    private val storageService: StorageService
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
        val imageStorageKey = image?.let { uploadImage(merchantId, productId, it, request.branchId) }
        val product = MerchantProduct(
            id = productId,
            merchantApplicationId = merchantId,
            name = request.name.trim(),
            description = ProductImagePolicy.normalizeDescription(request.description),
            unitPrice = request.unitPrice,
            imageStorageKey = imageStorageKey,
            active = active,
            merchantBranchId = request.branchId,
            productCategoryId = categoryId,
            productCode = resolvedProductCode
        )
        val saved = try {
            productRepository.save(product)
        } catch (ex: RuntimeException) {
            imageStorageKey?.let { cleanupUploadedImage(it) }
            throw ex
        }
        log.info("Product created: id={} merchant={}", saved.id, merchantId)
        return saved.toResponse()
    }

    @Transactional(readOnly = true)
    fun list(
        merchantId: UUID,
        merchantBranchId: UUID,
        categoryId: UUID? = null
    ): List<ProductResponse> {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val products = if (categoryId == null) {
            productRepository.findAllByMerchantBranchId(merchantBranchId)
        } else {
            validateCategory(merchantId, categoryId)
            productRepository.findAllByMerchantBranchIdAndProductCategoryId(merchantBranchId, categoryId)
        }
            .map { it.toResponse() }
        return products
    }

    @Transactional(readOnly = true)
    fun get(merchantId: UUID, productId: UUID, merchantBranchId: UUID?): ProductResponse {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        val product = if (merchantBranchId != null) {
            productRepository.findByIdAndMerchantBranchId(productId, merchantBranchId)
                .orElseThrow { NoSuchElementException("Product not found: $productId") }
        } else {
            productRepository.findById(productId)
                .orElseThrow { NoSuchElementException("Product not found: $productId") }
                .also {
                    if (it.merchantApplicationId != merchantId) throw NoSuchElementException("Product not found: $productId")
                }
        }
        return product.toResponse()
    }

    @Transactional(readOnly = true)
    fun findByName(merchantId: UUID, merchantBranchId: UUID, productName: String): ProductResponse? {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        return productRepository.findByNameAndMerchantApplicationIdAndMerchantBranchIdAndActiveTrue(
            productName,
            merchantId,
            merchantBranchId
        )?.toResponse()
    }

    @Transactional(readOnly = true)
    fun findByNameIncludingInactive(merchantId: UUID, merchantBranchId: UUID, productName: String): ProductResponse? {
        requireMerchantExists(merchantId)
        enableTenantFilter(merchantId)
        return productRepository.findByNameAndMerchantApplicationIdAndMerchantBranchId(
            productName,
            merchantId,
            merchantBranchId
        )?.toResponse()
    }

    @Transactional(readOnly = true)
    fun findByProductCode(merchantId: UUID, branchId: UUID, productCode: Long): ProductResponse? {
        requireMerchantExists(merchantId)
        return productRepository.findByMerchantBranchIdAndProductCode(branchId, productCode)
            ?.takeIf { it.merchantApplicationId == merchantId }
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
        require(merchantApplicationRepository.existsById(merchantId)) {
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
        require(merchantBranchRepository.existsByMerchantApplicationIdAndId(merchantId, branchId)) {
            "Merchant branch not found or does not belong to merchant: $branchId"
        }
    }

    private fun validateCategory(merchantId: UUID, categoryId: UUID?): UUID? {
        if (categoryId == null) return null
        require(categoryRepository.existsByIdAndMerchantApplicationId(categoryId, merchantId)) {
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
        val newImageStorageKey = image?.let { uploadImage(merchantId, product.id, it, request.branchId) } ?: previousImageStorageKey
        val resolvedProductCode = resolveProductCode(request.branchId, request.productCode)
        product.update(
            name = request.name.trim(),
            description = ProductImagePolicy.normalizeDescription(request.description),
            unitPrice = request.unitPrice,
            imageStorageKey = newImageStorageKey,
            productCategoryId = request.categoryId,
            productCode = resolvedProductCode
        )
        if (request.branchId != product.merchantBranchId) {
            product.merchantBranchId = request.branchId
        }
        val saved = try {
            productRepository.save(product)
        } catch (ex: RuntimeException) {
            if (newImageStorageKey != null && newImageStorageKey != previousImageStorageKey) {
                cleanupUploadedImage(newImageStorageKey)
            }
            throw ex
        }
        log.info("Product updated: id={} merchant={}", saved.id, merchantId)
        return saved.toResponse()
    }

    private fun uploadImage(merchantId: UUID, productId: UUID, image: ProductImageUpload, merchantBranchId: UUID): String {
        val storageKey = "merchants/$merchantId/branches/$merchantBranchId/products/$productId/image.${image.extension}"
        storageService.upload(storageKey, image.contentType, image.bytes)
        return storageKey
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

    private fun MerchantProduct.toResponse() = ProductResponse(
        categoryId = productCategoryId,
        categoryName = productCategoryId?.let {
            categoryRepository.findByMerchantApplicationIdAndId(merchantApplicationId, it)?.name
        },
        productCode = productCode,
        productId = id,
        branchId = merchantBranchId!!,
        name = name,
        description = description,
        unitPrice = unitPrice,
        imageUrl = signedImageUrl(imageStorageKey),
        active = active,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
