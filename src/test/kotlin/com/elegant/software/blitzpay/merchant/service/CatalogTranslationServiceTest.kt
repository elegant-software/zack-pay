package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.CatalogItemType
import com.elegant.software.blitzpay.merchant.api.CatalogTranslationInput
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogTranslationServiceTest {

    private val productRepository = mock<MerchantProductRepository>()
    private val categoryRepository = mock<MerchantProductCategoryRepository>()
    private val productTranslationRepository = mock<MerchantProductTranslationRepository>()
    private val categoryTranslationRepository = mock<MerchantProductCategoryTranslationRepository>()
    private val menuLanguageService = mock<MenuLanguageService>()
    private val service = CatalogTranslationService(
        productRepository,
        categoryRepository,
        productTranslationRepository,
        categoryTranslationRepository,
        menuLanguageService,
    )

    private val merchantId = UUID.randomUUID()
    private val category = MerchantProductCategory(merchantId = merchantId, name = "Hauptgerichte")
    private val untranslated = product("Brezel", "Frisch gebacken")
    private val current = product("Leberkäse", "Mit süßem Senf")
    private val stale = product("Schnitzel", "Vom Kalb")

    @BeforeEach
    fun setUp() {
        whenever(menuLanguageService.supportedLanguages(merchantId)).thenReturn(setOf("en"))
        whenever(menuLanguageService.originalLanguage(merchantId)).thenReturn("de")
        whenever(categoryRepository.findAllByMerchantId(merchantId)).thenReturn(listOf(category))
        whenever(categoryRepository.findByMerchantIdAndId(merchantId, category.id)).thenReturn(category)
        whenever(productRepository.findAllByMerchantIdAndActiveTrue(merchantId)).thenReturn(listOf(untranslated, current, stale))
        listOf(untranslated, current, stale).forEach {
            whenever(productRepository.findById(it.id)).thenReturn(Optional.of(it))
        }
        whenever(productTranslationRepository.save(any<MerchantProductTranslation>())).thenAnswer { it.arguments[0] }
        whenever(categoryTranslationRepository.save(any<MerchantProductCategoryTranslation>())).thenAnswer { it.arguments[0] }
    }

    @Test
    fun `pending lists missing and stale items but not current ones`() {
        val currentTranslation = translationOf(current, "Bavarian meatloaf", TranslationOrigin.MERCHANT)
        val staleTranslation = translationOf(stale, "Schnitzel", TranslationOrigin.MACHINE)
        stale.update(name = "Schnitzel", description = "Vom Schwein", unitPrice = stale.unitPrice, imageStorageKey = null)
        whenever(productTranslationRepository.findAllByProductIdInAndLanguage(any(), any()))
            .thenReturn(listOf(currentTranslation, staleTranslation))
        whenever(categoryTranslationRepository.findAllByCategoryIdInAndLanguage(any(), any())).thenReturn(emptyList())

        val pending = service.pending(merchantId, "en")

        assertEquals("de", pending.originalMenuLanguage)
        assertEquals(3, pending.totalPending)
        assertEquals(listOf(category.id, untranslated.id, stale.id), pending.items.map { it.id })
        val staleItem = pending.items.single { it.id == stale.id }
        assertTrue(staleItem.stale)
        assertEquals("Vom Schwein", staleItem.originalDescription)
        assertEquals(TranslationOrigin.MACHINE, staleItem.origin)
    }

    @Test
    fun `pending honours the limit but reports the total`() {
        whenever(productTranslationRepository.findAllByProductIdInAndLanguage(any(), any())).thenReturn(emptyList())
        whenever(categoryTranslationRepository.findAllByCategoryIdInAndLanguage(any(), any())).thenReturn(emptyList())

        val pending = service.pending(merchantId, "en", limit = 2)

        assertEquals(4, pending.totalPending)
        assertEquals(2, pending.items.size)
    }

    @Test
    fun `price change does not make a translation stale`() {
        val translation = translationOf(current, "Bavarian meatloaf", TranslationOrigin.MACHINE)
        current.update(name = current.name, description = current.description, unitPrice = BigDecimal("99.00"), imageStorageKey = "new.jpg")
        whenever(productTranslationRepository.findAllByProductId(current.id)).thenReturn(listOf(translation))

        assertFalse(service.productTranslations(merchantId, current.id).single().stale)
    }

    @Test
    fun `machine upsert skips current merchant translations and overwrites stale ones`() {
        val currentMerchant = translationOf(current, "Bavarian meatloaf", TranslationOrigin.MERCHANT)
        val staleMerchant = translationOf(stale, "Veal schnitzel", TranslationOrigin.MERCHANT)
        stale.update(name = "Schnitzel", description = "Vom Schwein", unitPrice = stale.unitPrice, imageStorageKey = null)
        whenever(productTranslationRepository.findByProductIdAndLanguage(current.id, "en")).thenReturn(currentMerchant)
        whenever(productTranslationRepository.findByProductIdAndLanguage(stale.id, "en")).thenReturn(staleMerchant)

        val result = service.bulkUpsertMachine(
            merchantId, "en",
            listOf(
                CatalogTranslationInput(CatalogItemType.PRODUCT, current.id.toString(), "Liver cheese"),
                CatalogTranslationInput(CatalogItemType.PRODUCT, stale.id.toString(), "Pork schnitzel", "Pork"),
                CatalogTranslationInput(CatalogItemType.PRODUCT, untranslated.id.toString(), "Pretzel", "Freshly baked"),
                CatalogTranslationInput(CatalogItemType.CATEGORY, category.id.toString(), "Main dishes"),
                CatalogTranslationInput(CatalogItemType.PRODUCT, UUID.randomUUID().toString(), "Ghost"),
            )
        )

        assertEquals(listOf(current.id.toString()), result.skipped.map { it.existingId })
        assertEquals(listOf(stale.id), result.updated.map { it.id })
        assertEquals(listOf(untranslated.id, category.id), result.created.map { it.id })
        assertEquals(listOf("Ghost"), result.failed.map { it.name })
        assertEquals("Bavarian meatloaf", currentMerchant.name)
        assertEquals("Pork schnitzel", staleMerchant.name)
        assertEquals(TranslationOrigin.MACHINE, staleMerchant.origin)
        assertEquals(CatalogTranslationService.sourceHash(stale), staleMerchant.sourceHash)
    }

    @Test
    fun `merchant put records merchant origin`() {
        val saved = service.putProductTranslation(
            merchantId, untranslated.id, "EN", UpsertCatalogTranslationRequest(" Pretzel ", "Freshly baked")
        )

        assertEquals("en", saved.language)
        assertEquals("Pretzel", saved.name)
        assertEquals(TranslationOrigin.MERCHANT, saved.origin)
        assertFalse(saved.stale)
    }

    @Test
    fun `translations are only accepted for supported languages`() {
        assertFailsWith<IllegalArgumentException> {
            service.putProductTranslation(merchantId, untranslated.id, "fr", UpsertCatalogTranslationRequest("Bretzel"))
        }
        verify(productTranslationRepository, never()).save(any<MerchantProductTranslation>())
    }

    @Test
    fun `products of another merchant are not found`() {
        val foreign = MerchantProduct(merchantId = UUID.randomUUID(), name = "X", unitPrice = BigDecimal.ONE)
        whenever(productRepository.findById(foreign.id)).thenReturn(Optional.of(foreign))

        assertFailsWith<NoSuchElementException> { service.productTranslations(merchantId, foreign.id) }
    }

    @Test
    fun `coverage counts only current translations`() {
        val currentTranslation = translationOf(current, "Bavarian meatloaf", TranslationOrigin.MACHINE)
        val staleTranslation = translationOf(stale, "Schnitzel", TranslationOrigin.MACHINE)
        stale.update(name = "Wiener Schnitzel", description = stale.description, unitPrice = stale.unitPrice, imageStorageKey = null)
        whenever(productTranslationRepository.findAllByProductIdInAndLanguage(any(), any()))
            .thenReturn(listOf(currentTranslation, staleTranslation))
        whenever(categoryTranslationRepository.findAllByCategoryIdInAndLanguage(any(), any())).thenReturn(emptyList())

        val coverage = service.coverage(merchantId).single()

        assertEquals("en", coverage.language)
        assertEquals(3, coverage.productsTotal)
        assertEquals(1, coverage.productsTranslated)
        assertEquals(1, coverage.categoriesTotal)
        assertEquals(0, coverage.categoriesTranslated)
    }

    @Test
    fun `served texts include stale translations`() {
        val staleTranslation = translationOf(stale, "Schnitzel", TranslationOrigin.MACHINE)
        stale.update(name = "Wiener Schnitzel", description = stale.description, unitPrice = stale.unitPrice, imageStorageKey = null)
        whenever(productTranslationRepository.findAllByProductIdInAndLanguage(listOf(stale.id), "en"))
            .thenReturn(listOf(staleTranslation))

        assertEquals("Schnitzel", service.productTexts(listOf(stale), "en")[stale.id]?.name)
        assertTrue(service.productTexts(listOf(stale), null).isEmpty())
    }

    private fun product(name: String, description: String) =
        MerchantProduct(merchantId = merchantId, name = name, description = description, unitPrice = BigDecimal("4.50"))

    private fun translationOf(product: MerchantProduct, name: String, origin: TranslationOrigin) =
        MerchantProductTranslation(
            productId = product.id,
            language = "en",
            name = name,
            origin = origin,
            sourceHash = CatalogTranslationService.sourceHash(product),
        )
}
