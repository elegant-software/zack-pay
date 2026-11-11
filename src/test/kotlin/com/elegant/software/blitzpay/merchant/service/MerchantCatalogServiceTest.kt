package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.config.ApiException
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategory
import com.elegant.software.blitzpay.merchant.persistence.repository.*
import com.elegant.software.blitzpay.storage.StorageService
import com.elegant.software.blitzpay.support.TestFixtureLoader
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.hibernate.Session
import org.hibernate.Filter
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.domain.Pageable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class MerchantCatalogServiceTest {
    private val fixture = TestFixtureLoader.load("testdata/merchant/mobile-catalog.json", CatalogFixture::class.java)
    private val products = mock<MerchantProductRepository>()
    private val categories = mock<MerchantProductCategoryRepository>()
    private val merchants = mock<MerchantRepository>()
    private val branches = mock<MerchantBranchRepository>()
    private val storage = mock<StorageService>()
    private val translations = mock<CatalogTranslationService>()
    private val languages = mock<MenuLanguageService>()
    private val entityManager = mock<EntityManager>()
    private val imageProcessor = mock<ProductImageProcessor>()
    private val service = MerchantProductService(products, categories, merchants, branches, entityManager, storage, translations, languages, imageProcessor)

    @BeforeEach
    fun setUp() {
        val session = mock<Session>()
        val filter = mock<Filter>()
        val query = mock<Query>()
        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.enableFilter(any())).thenReturn(filter)
        whenever(filter.setParameter(any<String>(), any())).thenReturn(filter)
        whenever(entityManager.createNativeQuery(any<String>())).thenReturn(query)
        whenever(query.setParameter(any<String>(), any())).thenReturn(query)
        whenever(merchants.existsById(fixture.merchantId)).thenReturn(true)
        whenever(branches.existsByMerchantIdAndIdAndActiveTrue(fixture.merchantId, fixture.branchId)).thenReturn(true)
        whenever(branches.existsByMerchantIdAndId(fixture.merchantId, fixture.branchId)).thenReturn(true)
        whenever(categories.existsByIdAndMerchantId(fixture.categoryId, fixture.merchantId)).thenReturn(true)
        whenever(storage.presignDownload(any(), any())).thenAnswer { "https://images.invalid/${it.getArgument<String>(0)}" }
    }

    @Test
    fun `limits database rows with lookahead and keeps full detail images separate from thumbnails`() {
        whenever(products.findCatalogPage(any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(fixture.productIds.map { product(it) })

        val page = page(limit = 2)

        assertEquals(fixture.productIds.take(2), page.items.map { it.productId })
        assertTrue(page.hasMore)
        assertEquals("https://images.invalid/${fixture.detailKey}", page.items.first().imageUrl)
        assertEquals("https://images.invalid/${fixture.thumbnailKey}", page.items.first().thumbnailUrl)
        val cursor = CatalogScope(fixture.merchantId, fixture.branchId, null, false, false).position(page.nextCursor)
        assertEquals(fixture.productIds[1], cursor?.productId)
        argumentCaptor<Pageable>().apply {
            verify(products).findCatalogPage(eq(fixture.merchantId), eq(fixture.branchId), isNull(), eq(false), eq(false), isNull(), isNull(), capture())
            assertEquals(3, firstValue.pageSize)
        }
        verify(products, never()).findAllByMerchantBranchId(any())
    }

    @Test
    fun `passes cursor tie-breaker and exact selection to repository`() {
        val cursor = CatalogScope(fixture.merchantId, fixture.branchId, fixture.categoryId, false, true)
            .cursor(fixture.createdAt, fixture.productIds[0])
        whenever(products.findCatalogPage(any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(listOf(product(fixture.productIds[1], active = false)))

        val page = page(categoryId = fixture.categoryId, includeInactive = true, cursor = cursor)

        assertFalse(page.hasMore)
        assertNull(page.nextCursor)
        assertFalse(page.items.single().active)
        verify(products).findCatalogPage(eq(fixture.merchantId), eq(fixture.branchId), eq(fixture.categoryId), eq(false), eq(true),
            eq(fixture.createdAt), eq(fixture.productIds[0]), any())
    }

    @Test
    fun `empty catalog is terminal and uncategorized is a database filter`() {
        whenever(products.findCatalogPage(any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), any())).thenReturn(emptyList())
        val page = page(uncategorized = true)
        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
        assertNull(page.nextCursor)
        verify(products).findCatalogPage(any(), any(), isNull(), eq(true), eq(false), isNull(), isNull(), any())
    }

    @Test
    fun `rejects malformed and cross-scope cursors before querying products`() {
        val scope = CatalogScope(fixture.merchantId, fixture.branchId, null, false, false)
        val cursor = scope.cursor(fixture.createdAt, fixture.productIds[0])
        for (invalid in listOf("not-a-cursor", "", "x".repeat(1025))) {
            assertEquals("INVALID_CATALOG_CURSOR", assertFailsWith<ApiException> { page(cursor = invalid) }.code)
        }
        assertEquals("INVALID_CATALOG_CURSOR", assertFailsWith<ApiException> { page(categoryId = fixture.categoryId, cursor = cursor) }.code)
        for (other in listOf(
            scope.copy(merchantId = UUID(0, 1)), scope.copy(branchId = UUID(0, 2)),
            scope.copy(uncategorized = true), scope.copy(includeInactive = true),
        )) {
            assertEquals("INVALID_CATALOG_CURSOR", assertFailsWith<ApiException> { other.position(cursor) }.code)
        }
        verifyNoInteractions(products)
    }

    @Test
    fun `validates limits filters and branch ownership before reading`() {
        assertFailsWith<IllegalArgumentException> { page(limit = 0) }
        assertFailsWith<IllegalArgumentException> { page(limit = 101) }
        assertFailsWith<IllegalArgumentException> { page(categoryId = fixture.categoryId, uncategorized = true) }
        whenever(branches.existsByMerchantIdAndIdAndActiveTrue(fixture.merchantId, fixture.branchId)).thenReturn(false)
        assertFailsWith<NoSuchElementException> { page() }
        verifyNoInteractions(products)
    }

    @Test
    fun `categories use branch counts translations and a separate uncategorized count`() {
        whenever(products.catalogCategoryCounts(fixture.merchantId, fixture.branchId, false))
            .thenReturn(listOf(Count(fixture.categoryId, 2), Count(null, 1)))
        whenever(categories.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(
            MerchantProductCategory(id = fixture.categoryId, merchantId = fixture.merchantId, name = fixture.categoryName, imageStorageKey = "categories/drinks.png")
        ))
        whenever(translations.categoryNames(any(), eq("de"))).thenReturn(mapOf(fixture.categoryId to fixture.translatedCategoryName))

        val result = service.catalogCategories(fixture.merchantId, fixture.branchId, false, "de")

        assertEquals(3L, result.totalCount)
        assertEquals(1L, result.uncategorizedCount)
        assertEquals(2L, result.categories.single().productCount)
        assertEquals(fixture.translatedCategoryName, result.categories.single().name)
        assertEquals("https://images.invalid/categories/drinks.png", result.categories.single().imageUrl)
        verify(products, never()).findAllByMerchantBranchId(any())
    }

    @Test
    fun `backfill creates only missing thumbnails and never overwrites detail images`() {
        val existing = product(fixture.productIds[0])
        val missing = product(fixture.productIds[1]).apply { thumbnailStorageKey = null }
        whenever(products.findCatalogPage(any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), any()))
            .thenReturn(listOf(existing, missing))
        val imageBytes = requireNotNull(javaClass.classLoader.getResourceAsStream("testdata/merchant/catalog-detail.png")).use { it.readBytes() }
        whenever(storage.download(fixture.detailKey)).thenReturn(imageBytes)
        whenever(imageProcessor.thumbnail(any())).thenReturn(ProductImageUpload("image/jpeg", byteArrayOf(1, 2)))
        whenever(products.attachCatalogThumbnail(eq(missing.id), eq(fixture.merchantId), eq(fixture.detailKey), eq(missing.updatedAt), any())).thenReturn(1)

        val result = service.backfillCatalogThumbnails(fixture.merchantId, fixture.branchId, 10, null)

        assertEquals(1, result.updated)
        assertEquals(1, result.skipped)
        assertEquals(0, result.failed)
        argumentCaptor<String>().apply {
            verify(storage).upload(capture(), eq("image/jpeg"), any())
            assertTrue(firstValue.contains("/thumbnail-"))
            assertNotEquals(fixture.detailKey, firstValue)
        }
        verify(storage, never()).delete(fixture.detailKey)
    }

    @Test
    fun `backfill cleans a generated variant when a concurrent edit wins`() {
        val missing = product(fixture.productIds[0]).apply { thumbnailStorageKey = null }
        whenever(products.findCatalogPage(any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), any())).thenReturn(listOf(missing))
        whenever(storage.download(any())).thenReturn(byteArrayOf(1))
        whenever(imageProcessor.thumbnail(any())).thenReturn(ProductImageUpload("image/jpeg", byteArrayOf(2)))
        whenever(products.attachCatalogThumbnail(any(), any(), any(), any(), any())).thenReturn(0)

        val result = service.backfillCatalogThumbnails(fixture.merchantId, fixture.branchId, 10, null)

        assertEquals(0, result.updated)
        assertEquals(1, result.skipped)
        argumentCaptor<String>().apply {
            verify(storage).delete(capture())
            assertTrue(firstValue.contains("/thumbnail-"))
        }
    }

    @Test
    fun `backfill records download failures and leaves original products unchanged`() {
        val missing = product(fixture.productIds[0]).apply { thumbnailStorageKey = null }
        whenever(products.findCatalogPage(any(), any(), anyOrNull(), any(), any(), anyOrNull(), anyOrNull(), any())).thenReturn(listOf(missing))
        whenever(storage.download(any())).thenThrow(IllegalStateException("Storage unavailable"))
        val result = service.backfillCatalogThumbnails(fixture.merchantId, fixture.branchId, 10, null)
        assertEquals(1, result.failed)
        assertEquals(0, result.updated)
        verify(products, never()).attachCatalogThumbnail(any(), any(), any(), any(), any())
        verify(storage, never()).upload(any(), any(), any())
    }

    private fun page(categoryId: UUID? = null, uncategorized: Boolean = false, includeInactive: Boolean = false, limit: Int = 24, cursor: String? = null) =
        service.catalogPage(fixture.merchantId, fixture.branchId, categoryId, uncategorized, includeInactive, limit, cursor, null)

    private fun product(id: UUID, active: Boolean = true) = MerchantProduct(
        id = id, merchantId = fixture.merchantId, merchantBranchId = fixture.branchId,
        name = "Product", unitPrice = BigDecimal.TEN, active = active, createdAt = fixture.createdAt,
        imageStorageKey = fixture.detailKey, thumbnailStorageKey = fixture.thumbnailKey,
    )

    private data class Count(override val categoryId: UUID?, override val productCount: Long) : CatalogCategoryCount
}

data class CatalogFixture(
    val merchantId: UUID, val branchId: UUID, val categoryId: UUID, val createdAt: Instant,
    val productIds: List<UUID>, val categoryName: String, val translatedCategoryName: String,
    val detailKey: String, val thumbnailKey: String,
)
