package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.UpdateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategory
import com.elegant.software.blitzpay.merchant.persistence.repository.*
import com.elegant.software.blitzpay.storage.StorageService
import com.elegant.software.blitzpay.support.TestFixtureLoader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class CategoryImageServiceTest {
    private val fixture = TestFixtureLoader.load("testdata/merchant/category-image.json", CategoryImageFixture::class.java)
    private val categories = mock<MerchantProductCategoryRepository>()
    private val merchants = mock<MerchantRepository>()
    private val products = mock<MerchantProductRepository>()
    private val storage = mock<StorageService>()
    private val service = MerchantProductCategoryService(categories, products, merchants, mock(), mock(), mock(), mock(), storage)
    private val image = ProductImageUpload("image/png", requireNotNull(javaClass.getResourceAsStream("/testdata/merchant/catalog-detail.png")).use { it.readBytes() })
    private val category = MerchantProductCategory(
        id = fixture.categoryId, merchantId = fixture.merchantId, name = fixture.name,
        createdAt = fixture.createdAt, imageStorageKey = fixture.previousKey,
    )

    @BeforeEach
    fun setup() {
        whenever(merchants.existsById(fixture.merchantId)).thenReturn(true)
        whenever(categories.findByMerchantIdAndId(fixture.merchantId, fixture.categoryId)).thenReturn(category)
        whenever(categories.save(any<MerchantProductCategory>())).thenAnswer { it.arguments[0] }
        whenever(storage.presignDownload(any(), any())).thenReturn(fixture.imageUrl)
    }

    @Test
    fun `create uploads image and exposes a signed URL`() {
        val response = service.create(fixture.merchantId, CreateProductCategoryRequest(fixture.name), image)
        assertEquals(fixture.imageUrl, response.imageUrl)
        val key = argumentCaptor<String>()
        verify(storage).upload(key.capture(), eq("image/png"), eq(image.bytes))
        assertTrue(key.firstValue.startsWith("merchants/${fixture.merchantId}/categories/${response.id}/"))
    }

    @Test
    fun `JSON update preserves existing image and list returns its URL`() {
        service.update(fixture.merchantId, fixture.categoryId, UpdateProductCategoryRequest(fixture.name))
        assertEquals(fixture.previousKey, category.imageStorageKey)
        whenever(categories.findAllByMerchantId(fixture.merchantId)).thenReturn(listOf(category))
        assertEquals(fixture.imageUrl, service.list(fixture.merchantId).single().imageUrl)
        verify(storage, never()).upload(any(), any(), any())
        verify(storage, never()).delete(any())
    }

    @Test
    fun `replace uses a new key and deletes previous image only after commit`() {
        withTransaction {
            service.update(fixture.merchantId, fixture.categoryId, UpdateProductCategoryRequest(fixture.name), image)
            assertNotEquals(fixture.previousKey, category.imageStorageKey)
            verify(storage, never()).delete(any())
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            verify(storage).delete(fixture.previousKey)
        }
    }

    @Test
    fun `rollback removes new image and preserves previous object`() {
        withTransaction {
            service.update(fixture.merchantId, fixture.categoryId, UpdateProductCategoryRequest(fixture.name), image)
            val newKey = requireNotNull(category.imageStorageKey)
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
            verify(storage).delete(newKey)
            verify(storage, never()).delete(fixture.previousKey)
        }
    }

    @Test
    fun `remove returns no image URL and cleans up the object`() {
        val response = service.update(fixture.merchantId, fixture.categoryId, UpdateProductCategoryRequest(fixture.name), null, removeImage = true)
        assertNull(response.imageUrl)
        assertNull(category.imageStorageKey)
        verify(storage).delete(fixture.previousKey)
    }

    @Test
    fun `category deletion cleans up image after commit`() {
        withTransaction {
            service.delete(fixture.merchantId, fixture.categoryId)
            verify(storage, never()).delete(any())
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            verify(storage).delete(fixture.previousKey)
        }
    }

    @Test
    fun `missing category cannot upload an image`() {
        whenever(categories.findByMerchantIdAndId(fixture.merchantId, fixture.categoryId)).thenReturn(null)
        assertFailsWith<NoSuchElementException> {
            service.update(fixture.merchantId, fixture.categoryId, UpdateProductCategoryRequest(fixture.name), image)
        }
        verify(storage, never()).upload(any(), any(), any())
    }

    @Test
    fun `upload and removal together are rejected before storage changes`() {
        assertFailsWith<IllegalArgumentException> {
            service.update(fixture.merchantId, fixture.categoryId, UpdateProductCategoryRequest(fixture.name), image, true)
        }
        verify(storage, never()).upload(any(), any(), any())
    }

    @Test
    fun `invalid content types oversized and empty images are rejected`() {
        assertFailsWith<IllegalArgumentException> { ProductImageUpload("text/plain", image.bytes) }
        assertFailsWith<IllegalArgumentException> { ProductImageUpload("image/png", ByteArray(5 * 1024 * 1024 + 1)) }
        assertFailsWith<IllegalArgumentException> {
            service.create(fixture.merchantId, CreateProductCategoryRequest(fixture.name), ProductImageUpload("image/png", byteArrayOf()))
        }
        verify(storage, never()).upload(any(), any(), any())
    }

    @Test
    fun `categories without images remain usable`() {
        category.imageStorageKey = null
        whenever(categories.findAllByMerchantId(fixture.merchantId)).thenReturn(listOf(category))
        assertNull(service.list(fixture.merchantId).single().imageUrl)
        verify(storage, never()).presignDownload(any(), any())
    }

    @Test
    fun `storage signing failure does not prevent category reads`() {
        whenever(categories.findAllByMerchantId(fixture.merchantId)).thenReturn(listOf(category))
        whenever(storage.presignDownload(any(), any())).thenThrow(IllegalStateException("storage unavailable"))
        assertNull(service.list(fixture.merchantId).single().imageUrl)
    }

    @Test
    fun `save failure cleans new upload without deleting previous image`() {
        whenever(categories.save(any<MerchantProductCategory>())).thenThrow(IllegalStateException("database unavailable"))
        assertFailsWith<IllegalStateException> {
            service.update(fixture.merchantId, fixture.categoryId, UpdateProductCategoryRequest(fixture.name), image)
        }
        verify(storage).delete(requireNotNull(category.imageStorageKey))
        verify(storage, never()).delete(fixture.previousKey)
    }

    private fun withTransaction(block: () -> Unit) {
        TransactionSynchronizationManager.initSynchronization()
        try { block() } finally { TransactionSynchronizationManager.clearSynchronization() }
    }
}

data class CategoryImageFixture(
    val merchantId: UUID, val categoryId: UUID, val createdAt: Instant,
    val name: String, val imageUrl: String, val previousKey: String,
)
