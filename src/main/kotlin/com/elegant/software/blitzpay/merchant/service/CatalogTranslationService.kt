package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.BulkFailedItem
import com.elegant.software.blitzpay.merchant.api.BulkSkippedItem
import com.elegant.software.blitzpay.merchant.api.CatalogItemType
import com.elegant.software.blitzpay.merchant.api.CatalogTranslationInput
import com.elegant.software.blitzpay.merchant.api.CatalogTranslationResponse
import com.elegant.software.blitzpay.merchant.api.CatalogTranslationUpsertResult
import com.elegant.software.blitzpay.merchant.api.PendingTranslationItem
import com.elegant.software.blitzpay.merchant.api.PendingTranslationsResponse
import com.elegant.software.blitzpay.merchant.api.TranslatedCatalogItem
import com.elegant.software.blitzpay.merchant.api.TranslationCoverageResponse
import com.elegant.software.blitzpay.merchant.api.UpsertCatalogTranslationRequest
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategory
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategoryTranslation
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductTranslation
import com.elegant.software.blitzpay.merchant.persistence.model.TranslationOrigin
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductCategoryTranslationRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductTranslationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

/**
 * Stores Product and Category Translations and applies the shared rules: a translation is stale when
 * the hash of the original text it was made from no longer matches, and a current `MERCHANT`
 * translation is never overwritten by a `MACHINE` one. See ADR-0020.
 */
@Service
@Transactional
class CatalogTranslationService(
    private val productRepository: MerchantProductRepository,
    private val categoryRepository: MerchantProductCategoryRepository,
    private val productTranslationRepository: MerchantProductTranslationRepository,
    private val categoryTranslationRepository: MerchantProductCategoryTranslationRepository,
    private val menuLanguageService: MenuLanguageService,
) {
    private val log = LoggerFactory.getLogger(CatalogTranslationService::class.java)

    @Transactional(readOnly = true)
    fun productTranslations(merchantId: UUID, productId: UUID): List<CatalogTranslationResponse> {
        val product = requireProduct(merchantId, productId)
        val hash = sourceHash(product)
        return productTranslationRepository.findAllByProductId(productId)
            .sortedBy { it.language }
            .map { it.toResponse(hash) }
    }

    fun putProductTranslation(
        merchantId: UUID,
        productId: UUID,
        language: String,
        request: UpsertCatalogTranslationRequest,
    ): CatalogTranslationResponse {
        val product = requireProduct(merchantId, productId)
        val lang = requireSupportedLanguage(merchantId, language)
        val saved = writeProduct(product, lang, request.name, request.description, TranslationOrigin.MERCHANT)
        log.info("Product translation saved by merchant: product={} language={}", productId, lang)
        return saved.toResponse(sourceHash(product))
    }

    fun deleteProductTranslation(merchantId: UUID, productId: UUID, language: String) {
        requireProduct(merchantId, productId)
        val translation = productTranslationRepository.findByProductIdAndLanguage(productId, MenuLanguageService.normalize(language))
            ?: throw NoSuchElementException("Translation not found: product=$productId language=$language")
        productTranslationRepository.delete(translation)
    }

    @Transactional(readOnly = true)
    fun categoryTranslations(merchantId: UUID, categoryId: UUID): List<CatalogTranslationResponse> {
        val category = requireCategory(merchantId, categoryId)
        val hash = sourceHash(category)
        return categoryTranslationRepository.findAllByCategoryId(categoryId)
            .sortedBy { it.language }
            .map { it.toResponse(hash) }
    }

    fun putCategoryTranslation(
        merchantId: UUID,
        categoryId: UUID,
        language: String,
        request: UpsertCatalogTranslationRequest,
    ): CatalogTranslationResponse {
        val category = requireCategory(merchantId, categoryId)
        val lang = requireSupportedLanguage(merchantId, language)
        val saved = writeCategory(category, lang, request.name, TranslationOrigin.MERCHANT)
        log.info("Category translation saved by merchant: category={} language={}", categoryId, lang)
        return saved.toResponse(sourceHash(category))
    }

    fun deleteCategoryTranslation(merchantId: UUID, categoryId: UUID, language: String) {
        requireCategory(merchantId, categoryId)
        val translation = categoryTranslationRepository.findByCategoryIdAndLanguage(categoryId, MenuLanguageService.normalize(language))
            ?: throw NoSuchElementException("Translation not found: category=$categoryId language=$language")
        categoryTranslationRepository.delete(translation)
    }

    /** Active Products and all Product Categories with no translation in [language], or only a stale one. */
    @Transactional(readOnly = true)
    fun pending(merchantId: UUID, language: String, limit: Int = Int.MAX_VALUE): PendingTranslationsResponse {
        require(limit > 0) { "limit must be > 0" }
        val lang = requireSupportedLanguage(merchantId, language)
        val original = requireNotNull(menuLanguageService.originalLanguage(merchantId)) {
            "Merchant $merchantId has no originalMenuLanguage"
        }
        val categories = categoryRepository.findAllByMerchantId(merchantId).sortedBy { it.name.lowercase() }
        val categoryTranslations = categoryTranslationRepository
            .findAllByCategoryIdInAndLanguage(categories.map { it.id }, lang)
            .associateBy { it.categoryId }
        val products = productRepository.findAllByMerchantIdAndActiveTrue(merchantId).sortedBy { it.name.lowercase() }
        val productTranslations = productTranslationRepository
            .findAllByProductIdInAndLanguage(products.map { it.id }, lang)
            .associateBy { it.productId }

        val categoryItems = categories.mapNotNull { category ->
            val translation = categoryTranslations[category.id]
            val stale = translation != null && translation.sourceHash != sourceHash(category)
            if (translation != null && !stale) return@mapNotNull null
            PendingTranslationItem(
                type = CatalogItemType.CATEGORY,
                id = category.id,
                originalName = category.name,
                translatedName = translation?.name,
                origin = translation?.origin,
                stale = stale,
            )
        }
        val productItems = products.mapNotNull { product ->
            val translation = productTranslations[product.id]
            val stale = translation != null && translation.sourceHash != sourceHash(product)
            if (translation != null && !stale) return@mapNotNull null
            PendingTranslationItem(
                type = CatalogItemType.PRODUCT,
                id = product.id,
                originalName = product.name,
                originalDescription = product.description,
                translatedName = translation?.name,
                translatedDescription = translation?.description,
                origin = translation?.origin,
                stale = stale,
            )
        }
        val items = categoryItems + productItems
        return PendingTranslationsResponse(
            merchantId = merchantId,
            originalMenuLanguage = original,
            language = lang,
            totalPending = items.size,
            items = items.take(limit),
        )
    }

    /** Writes `MACHINE` translations; skips items whose current translation was authored by the merchant. */
    fun bulkUpsertMachine(
        merchantId: UUID,
        language: String,
        items: List<CatalogTranslationInput>,
    ): CatalogTranslationUpsertResult {
        val lang = requireSupportedLanguage(merchantId, language)
        val created = mutableListOf<TranslatedCatalogItem>()
        val updated = mutableListOf<TranslatedCatalogItem>()
        val skipped = mutableListOf<BulkSkippedItem>()
        val failed = mutableListOf<BulkFailedItem>()

        for (input in items) {
            runCatching {
                val id = UUID.fromString(input.id.trim())
                when (input.type) {
                    CatalogItemType.PRODUCT -> {
                        val product = requireProduct(merchantId, id)
                        val existing = productTranslationRepository.findByProductIdAndLanguage(id, lang)
                        if (existing.isCurrentMerchantTranslation(sourceHash(product))) {
                            skipped.add(BulkSkippedItem(input.name, MERCHANT_SKIP_REASON, id.toString()))
                        } else {
                            val saved = writeProduct(product, lang, input.name, input.description, TranslationOrigin.MACHINE)
                            (if (existing == null) created else updated)
                                .add(TranslatedCatalogItem(CatalogItemType.PRODUCT, id, saved.name))
                        }
                    }
                    CatalogItemType.CATEGORY -> {
                        val category = requireCategory(merchantId, id)
                        val existing = categoryTranslationRepository.findByCategoryIdAndLanguage(id, lang)
                        if (existing.isCurrentMerchantTranslation(sourceHash(category))) {
                            skipped.add(BulkSkippedItem(input.name, MERCHANT_SKIP_REASON, id.toString()))
                        } else {
                            val saved = writeCategory(category, lang, input.name, TranslationOrigin.MACHINE)
                            (if (existing == null) created else updated)
                                .add(TranslatedCatalogItem(CatalogItemType.CATEGORY, id, saved.name))
                        }
                    }
                }
            }.onFailure { ex ->
                failed.add(BulkFailedItem(name = input.name, reason = ex.message ?: "Unknown error"))
            }
        }
        log.info(
            "Machine translations upserted: merchant={} language={} created={} updated={} skipped={} failed={}",
            merchantId, lang, created.size, updated.size, skipped.size, failed.size
        )
        return CatalogTranslationUpsertResult(created = created, updated = updated, skipped = skipped, failed = failed)
    }

    @Transactional(readOnly = true)
    fun coverage(merchantId: UUID): List<TranslationCoverageResponse> {
        val products = productRepository.findAllByMerchantIdAndActiveTrue(merchantId)
        val categories = categoryRepository.findAllByMerchantId(merchantId)
        val productHashes = products.associate { it.id to sourceHash(it) }
        val categoryHashes = categories.associate { it.id to sourceHash(it) }
        return menuLanguageService.supportedLanguages(merchantId).map { lang ->
            TranslationCoverageResponse(
                language = lang,
                productsTotal = products.size,
                productsTranslated = productTranslationRepository
                    .findAllByProductIdInAndLanguage(productHashes.keys, lang)
                    .count { it.sourceHash == productHashes[it.productId] },
                categoriesTotal = categories.size,
                categoriesTranslated = categoryTranslationRepository
                    .findAllByCategoryIdInAndLanguage(categoryHashes.keys, lang)
                    .count { it.sourceHash == categoryHashes[it.categoryId] },
            )
        }
    }

    /** Translations to serve for [products] in [language], keyed by product id. Stale ones are included. */
    @Transactional(readOnly = true)
    fun productTexts(products: Collection<MerchantProduct>, language: String?): Map<UUID, MerchantProductTranslation> =
        if (language == null || products.isEmpty()) emptyMap()
        else productTranslationRepository.findAllByProductIdInAndLanguage(products.map { it.id }, language)
            .associateBy { it.productId }

    /** Translated category names to serve in [language], keyed by category id. Stale ones are included. */
    @Transactional(readOnly = true)
    fun categoryNames(categoryIds: Collection<UUID>, language: String?): Map<UUID, String> =
        if (language == null || categoryIds.isEmpty()) emptyMap()
        else categoryTranslationRepository.findAllByCategoryIdInAndLanguage(categoryIds, language)
            .associate { it.categoryId to it.name }

    private fun writeProduct(
        product: MerchantProduct,
        language: String,
        name: String,
        description: String?,
        origin: TranslationOrigin,
    ): MerchantProductTranslation {
        val normalizedName = normalizeName(name, MAX_PRODUCT_NAME_LENGTH)
        val normalizedDescription = ProductImagePolicy.normalizeDescription(description)
        val hash = sourceHash(product)
        val translation = productTranslationRepository.findByProductIdAndLanguage(product.id, language)
            ?.apply { revise(normalizedName, normalizedDescription, origin, hash) }
            ?: MerchantProductTranslation(
                productId = product.id,
                language = language,
                name = normalizedName,
                description = normalizedDescription,
                origin = origin,
                sourceHash = hash,
            )
        return productTranslationRepository.save(translation)
    }

    private fun writeCategory(
        category: MerchantProductCategory,
        language: String,
        name: String,
        origin: TranslationOrigin,
    ): MerchantProductCategoryTranslation {
        val normalizedName = normalizeName(name, MAX_CATEGORY_NAME_LENGTH)
        val hash = sourceHash(category)
        val translation = categoryTranslationRepository.findByCategoryIdAndLanguage(category.id, language)
            ?.apply { revise(normalizedName, origin, hash) }
            ?: MerchantProductCategoryTranslation(
                categoryId = category.id,
                language = language,
                name = normalizedName,
                origin = origin,
                sourceHash = hash,
            )
        return categoryTranslationRepository.save(translation)
    }

    private fun requireSupportedLanguage(merchantId: UUID, language: String): String {
        val lang = MenuLanguageService.normalize(language)
        require(lang in menuLanguageService.supportedLanguages(merchantId)) {
            "Menu language '$lang' is not a supported menu language of merchant $merchantId"
        }
        return lang
    }

    private fun requireProduct(merchantId: UUID, productId: UUID): MerchantProduct =
        productRepository.findById(productId)
            .filter { it.merchantId == merchantId }
            .orElseThrow { NoSuchElementException("Product not found: $productId") }

    private fun requireCategory(merchantId: UUID, categoryId: UUID): MerchantProductCategory =
        categoryRepository.findByMerchantIdAndId(merchantId, categoryId)
            ?: throw NoSuchElementException("Category not found: $categoryId")

    private fun MerchantProductTranslation?.isCurrentMerchantTranslation(hash: String) =
        this != null && origin == TranslationOrigin.MERCHANT && sourceHash == hash

    private fun MerchantProductCategoryTranslation?.isCurrentMerchantTranslation(hash: String) =
        this != null && origin == TranslationOrigin.MERCHANT && sourceHash == hash

    private fun MerchantProductTranslation.toResponse(currentHash: String) = CatalogTranslationResponse(
        language = language,
        name = name,
        description = description,
        origin = origin,
        stale = sourceHash != currentHash,
        updatedAt = updatedAt,
    )

    private fun MerchantProductCategoryTranslation.toResponse(currentHash: String) = CatalogTranslationResponse(
        language = language,
        name = name,
        origin = origin,
        stale = sourceHash != currentHash,
        updatedAt = updatedAt,
    )

    companion object {
        private const val MAX_PRODUCT_NAME_LENGTH = 255
        private const val MAX_CATEGORY_NAME_LENGTH = 100
        private const val MERCHANT_SKIP_REASON = "current translation was edited by the merchant"

        fun sourceHash(product: MerchantProduct): String = sha256(product.name, product.description)

        fun sourceHash(category: MerchantProductCategory): String = sha256(category.name, null)

        private fun sha256(name: String, description: String?): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(name.toByteArray())
            digest.update(0)
            digest.update((description ?: "").toByteArray())
            return HexFormat.of().formatHex(digest.digest())
        }

        private fun normalizeName(name: String, maxLength: Int): String {
            val normalized = name.trim()
            require(normalized.isNotEmpty()) { "Translated name must not be blank" }
            require(normalized.length <= maxLength) { "Translated name must be <= $maxLength characters" }
            return normalized
        }
    }
}
