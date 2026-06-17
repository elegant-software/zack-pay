package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.UpdateProductRequest
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategory
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import com.elegant.software.blitzpay.storage.StorageService
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.hibernate.Filter
import org.hibernate.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MerchantProductServiceTest {

    private val productRepository = mock<MerchantProductRepository>()
    private val categoryRepository = mock<MerchantProductCategoryRepository>()
    private val merchantApplicationRepository = mock<MerchantApplicationRepository>()
    private val merchantBranchRepository = mock<com.elegant.software.blitzpay.merchant.persistence.repository.MerchantBranchRepository>()
    private val entityManager = mock<EntityManager>()
    private val storageService = mock<StorageService>()
    private val session = mock<Session>()
    private val hibernateFilter = mock<Filter>()
    private val nativeQuery = mock<Query>()

    private val service = MerchantProductService(
        productRepository,
        categoryRepository,
        merchantApplicationRepository,
        merchantBranchRepository,
        entityManager,
        storageService
    )
    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.enableFilter(any())).thenReturn(hibernateFilter)
        whenever(hibernateFilter.setParameter(any<String>(), any())).thenReturn(hibernateFilter)
        whenever(entityManager.createNativeQuery(any<String>())).thenReturn(nativeQuery)
        whenever(nativeQuery.setParameter(any<String>(), any())).thenReturn(nativeQuery)
        whenever(nativeQuery.singleResult).thenReturn(merchantId.toString())
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(merchantBranchRepository.existsByMerchantApplicationIdAndId(merchantId, branchId)).thenReturn(true)
        whenever(merchantBranchRepository.existsByMerchantApplicationIdAndIdAndActiveTrue(merchantId, branchId)).thenReturn(true)
        whenever(categoryRepository.existsByIdAndMerchantApplicationId(categoryId, merchantId)).thenReturn(true)
        whenever(storageService.presignDownload(any(), any())).thenAnswer { "https://signed.example/${it.arguments[0]}" }
    }

    @Test
    fun `create saves product with correct fields`() {
        val request = CreateProductRequest(
            name = "Coffee Blend",
            branchId = branchId,
            description = "**Medium roast**",
            unitPrice = BigDecimal("12.50")
        )
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, request)

        assertEquals("Coffee Blend", response.name)
        assertEquals("**Medium roast**", response.description)
        assertEquals(BigDecimal("12.50"), response.unitPrice)
        assertEquals(1L, response.productCode)
        assertTrue(response.active)
    }

    @Test
    fun `create allows zero-price products`() {
        val request = CreateProductRequest(name = "Free Sample", branchId = branchId, unitPrice = BigDecimal.ZERO)
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, request)

        assertEquals(BigDecimal.ZERO, response.unitPrice)
    }

    @Test
    fun `create can persist inactive products`() {
        val request = CreateProductRequest(name = "Hidden Sample", branchId = branchId, unitPrice = BigDecimal("2.00"))
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, request, active = false)

        assertEquals(false, response.active)
        assertEquals("INACTIVE", response.status)
    }

    @Test
    fun `create rejects negative price`() {
        val request = CreateProductRequest(name = "Invalid", branchId = branchId, unitPrice = BigDecimal("-1.00"))

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `create rejects blank product name`() {
        val request = CreateProductRequest(name = "   ", branchId = branchId, unitPrice = BigDecimal("5.00"))

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `create rejects overlong description`() {
        val request = CreateProductRequest(
            name = "Coffee",
            branchId = branchId,
            description = "x".repeat(2_001),
            unitPrice = BigDecimal("10.00")
        )

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `create uploads valid image and stores object key`() {
        val request = CreateProductRequest(name = "Coffee", branchId = branchId, unitPrice = BigDecimal("10.00"))
        val upload = ProductImageUpload(contentType = "image/webp", bytes = byteArrayOf(1, 2, 3))
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, request, upload)

        verify(storageService).upload(
            eq("merchants/$merchantId/branches/$branchId/products/${response.productId}/image.webp"),
            eq("image/webp"),
            eq(byteArrayOf(1, 2, 3))
        )
        assertEquals("https://signed.example/merchants/$merchantId/branches/$branchId/products/${response.productId}/image.webp", response.imageUrl)
    }

    @Test
    fun `create with valid category sets it on product`() {
        val request = CreateProductRequest(
            name = "Coffee",
            branchId = branchId,
            unitPrice = BigDecimal("10.00"),
            categoryId = categoryId
        )
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, categoryId)).thenReturn(
            MerchantProductCategory(merchantApplicationId = merchantId, id = categoryId, name = "Drinks")
        )

        val response = service.create(merchantId, request)

        assertEquals(categoryId, response.categoryId)
        assertEquals("Drinks", response.categoryName)
    }

    @Test
    fun `create keeps caller supplied product code`() {
        val request = CreateProductRequest(
            name = "Coffee",
            branchId = branchId,
            unitPrice = BigDecimal("10.00"),
            productCode = 12L
        )
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, request)

        assertEquals(12L, response.productCode)
    }

    @Test
    fun `create auto generates next highest product code within branch`() {
        val request = CreateProductRequest(
            name = "Coffee",
            branchId = branchId,
            unitPrice = BigDecimal("10.00")
        )
        whenever(productRepository.findMaxProductCodeByMerchantBranchId(branchId)).thenReturn(12L)
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, request)

        assertEquals(13L, response.productCode)
    }

    @Test
    fun `create with duplicate branch product code updates existing product instead of creating duplicate`() {
        val existing = MerchantProduct(
            merchantApplicationId = merchantId,
            merchantBranchId = branchId,
            name = "Existing Coffee",
            unitPrice = BigDecimal("9.00"),
            productCode = 12L
        )
        val request = CreateProductRequest(
            name = "Renamed Coffee",
            branchId = branchId,
            unitPrice = BigDecimal("10.00"),
            description = "updated",
            productCode = 12L
        )
        whenever(productRepository.findByMerchantBranchIdAndProductCode(branchId, 12L)).thenReturn(existing)
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, request)

        assertEquals(existing.id, response.productId)
        assertEquals("Renamed Coffee", response.name)
        verify(productRepository, never()).findMaxProductCodeByMerchantBranchId(branchId)
    }

    @Test
    fun `create with category from different merchant throws`() {
        whenever(categoryRepository.existsByIdAndMerchantApplicationId(categoryId, merchantId)).thenReturn(false)
        val request = CreateProductRequest(
            name = "Coffee",
            branchId = branchId,
            unitPrice = BigDecimal("10.00"),
            categoryId = categoryId
        )

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `update uploads valid image and replaces object key`() {
        val productId = UUID.randomUUID()
        val product = MerchantProduct(
            id = productId,
            merchantApplicationId = merchantId,
            name = "Coffee",
            unitPrice = BigDecimal("10.00"),
            imageStorageKey = "old-key",
            merchantBranchId = branchId
        )
        val request = UpdateProductRequest(
            name = "Coffee Updated",
            branchId = branchId,
            description = "_Updated_",
            unitPrice = BigDecimal("12.00")
        )
        val upload = ProductImageUpload(contentType = "image/png", bytes = byteArrayOf(4, 5, 6))
        whenever(productRepository.findByIdAndMerchantBranchId(productId, branchId)).thenReturn(Optional.of(product))
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.update(merchantId, productId, request, upload)

        verify(storageService).upload(
            eq("merchants/$merchantId/branches/$branchId/products/$productId/image.png"),
            eq("image/png"),
            eq(byteArrayOf(4, 5, 6))
        )
        assertEquals("Coffee Updated", response.name)
        assertEquals("_Updated_", response.description)
        assertEquals("https://signed.example/merchants/$merchantId/branches/$branchId/products/$productId/image.png", response.imageUrl)
    }

    @Test
    fun `update with valid category changes assignment`() {
        val productId = UUID.randomUUID()
        val product = MerchantProduct(
            id = productId,
            merchantApplicationId = merchantId,
            name = "Coffee",
            unitPrice = BigDecimal("10.00"),
            merchantBranchId = branchId
        )
        whenever(productRepository.findByIdAndMerchantBranchId(productId, branchId)).thenReturn(Optional.of(product))
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, categoryId)).thenReturn(
            MerchantProductCategory(merchantApplicationId = merchantId, id = categoryId, name = "Drinks")
        )

        val response = service.update(
            merchantId,
            productId,
            UpdateProductRequest(
                name = "Coffee",
                branchId = branchId,
                unitPrice = BigDecimal("10.00"),
                categoryId = categoryId
            )
        )

        assertEquals(categoryId, response.categoryId)
        assertEquals("Drinks", response.categoryName)
    }

    @Test
    fun `update with duplicate branch product code targets existing product`() {
        val productId = UUID.randomUUID()
        val targetProductId = UUID.randomUUID()
        val source = MerchantProduct(
            id = productId,
            merchantApplicationId = merchantId,
            name = "Source Coffee",
            unitPrice = BigDecimal("10.00"),
            merchantBranchId = branchId,
            productCode = 9L
        )
        val target = MerchantProduct(
            id = targetProductId,
            merchantApplicationId = merchantId,
            name = "Target Coffee",
            unitPrice = BigDecimal("11.00"),
            merchantBranchId = branchId,
            productCode = 12L
        )
        whenever(productRepository.findByIdAndMerchantBranchId(productId, branchId)).thenReturn(Optional.of(source))
        whenever(productRepository.findByMerchantBranchIdAndProductCode(branchId, 12L)).thenReturn(target)
        whenever(productRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }

        val response = service.update(
            merchantId,
            productId,
            UpdateProductRequest(
                name = "Updated Coffee",
                branchId = branchId,
                unitPrice = BigDecimal("12.00"),
                productCode = 12L
            )
        )

        assertEquals(targetProductId, response.productId)
        assertEquals("Updated Coffee", response.name)
        assertEquals(12L, response.productCode)
    }

    @Test
    fun `create rejects non positive product code`() {
        val request = CreateProductRequest(
            name = "Coffee",
            branchId = branchId,
            unitPrice = BigDecimal("10.00"),
            productCode = 0L
        )

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `invalid image type is rejected before storage upload`() {
        assertFailsWith<IllegalArgumentException> {
            ProductImageUpload(contentType = "text/plain", bytes = byteArrayOf(1))
        }
        verify(storageService, never()).upload(any(), any(), any())
    }

    @Test
    fun `oversized image is rejected before storage upload`() {
        assertFailsWith<IllegalArgumentException> {
            ProductImageUpload(contentType = "image/jpeg", bytes = ByteArray((ProductImagePolicy.MaxBytes + 1).toInt()))
        }
        verify(storageService, never()).upload(any(), any(), any())
    }

    @Test
    fun `storage upload failure does not persist product`() {
        val request = CreateProductRequest(name = "Coffee", branchId = branchId, unitPrice = BigDecimal("10.00"))
        val upload = ProductImageUpload(contentType = "image/png", bytes = byteArrayOf(1))
        whenever(storageService.upload(any(), any(), any())).thenThrow(IllegalStateException("storage down"))

        assertFailsWith<IllegalStateException> {
            service.create(merchantId, request, upload)
        }
        verify(productRepository, never()).save(any<MerchantProduct>())
    }

    @Test
    fun `list returns signed image urls from storage keys`() {
        val product = MerchantProduct(
            merchantApplicationId = merchantId,
            name = "Coffee",
            unitPrice = BigDecimal("10.00"),
            imageStorageKey = "merchants/$merchantId/branches/$branchId/products/product/image.jpg",
            merchantBranchId = branchId
        )
        whenever(productRepository.findAllByMerchantBranchId(branchId)).thenReturn(listOf(product))

        val response = service.list(merchantId, branchId)

        assertEquals("https://signed.example/merchants/$merchantId/branches/$branchId/products/product/image.jpg", response.single().imageUrl)
    }

    @Test
    fun `list returns inactive products for the branch`() {
        val inactiveProduct = MerchantProduct(
            merchantApplicationId = merchantId,
            name = "Archived Coffee",
            unitPrice = BigDecimal("10.00"),
            active = false,
            merchantBranchId = branchId
        )
        whenever(productRepository.findAllByMerchantBranchId(branchId)).thenReturn(listOf(inactiveProduct))

        val response = service.list(merchantId, branchId)

        assertEquals(1, response.size)
        assertEquals(false, response.single().active)
        verify(productRepository).findAllByMerchantBranchId(branchId)
    }

    @Test
    fun `list with category filter uses category scoped repository method`() {
        val product = MerchantProduct(
            merchantApplicationId = merchantId,
            name = "Coffee",
            unitPrice = BigDecimal("10.00"),
            merchantBranchId = branchId,
            productCategoryId = categoryId
        )
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, categoryId)).thenReturn(
            MerchantProductCategory(merchantApplicationId = merchantId, id = categoryId, name = "Drinks")
        )
        whenever(productRepository.findAllByMerchantBranchIdAndProductCategoryId(branchId, categoryId))
            .thenReturn(listOf(product))

        val response = service.list(merchantId, branchId, categoryId)

        assertEquals(1, response.size)
        assertEquals("Drinks", response.single().categoryName)
    }

    @Test
    fun `list with category filter returns inactive products`() {
        val product = MerchantProduct(
            merchantApplicationId = merchantId,
            name = "Archived Coffee",
            unitPrice = BigDecimal("10.00"),
            active = false,
            merchantBranchId = branchId,
            productCategoryId = categoryId
        )
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, categoryId)).thenReturn(
            MerchantProductCategory(merchantApplicationId = merchantId, id = categoryId, name = "Drinks")
        )
        whenever(productRepository.findAllByMerchantBranchIdAndProductCategoryId(branchId, categoryId))
            .thenReturn(listOf(product))

        val response = service.list(merchantId, branchId, categoryId)

        assertEquals(1, response.size)
        assertEquals(false, response.single().active)
        assertEquals("Drinks", response.single().categoryName)
        verify(productRepository).findAllByMerchantBranchIdAndProductCategoryId(branchId, categoryId)
    }

    @Test
    fun `get returns null category fields when uncategorised`() {
        val productId = UUID.randomUUID()
        val product = MerchantProduct(
            id = productId,
            merchantApplicationId = merchantId,
            name = "Coffee",
            unitPrice = BigDecimal("10.00"),
            merchantBranchId = branchId
        )
        whenever(productRepository.findByIdAndMerchantBranchId(productId, branchId)).thenReturn(Optional.of(product))

        val response = service.get(merchantId, productId, branchId)

        assertEquals(null, response.categoryId)
        assertEquals(null, response.categoryName)
    }

    @Test
    fun `get returns null image url when signing fails`() {
        val productId = UUID.randomUUID()
        val product = MerchantProduct(
            id = productId,
            merchantApplicationId = merchantId,
            name = "Coffee",
            unitPrice = BigDecimal("10.00"),
            imageStorageKey = "missing",
            merchantBranchId = branchId
        )
        whenever(productRepository.findByIdAndMerchantBranchId(productId, branchId)).thenReturn(Optional.of(product))
        whenever(storageService.presignDownload(eq("missing"), any())).thenThrow(IllegalStateException("not found"))

        val response = service.get(merchantId, productId, branchId)

        assertEquals(null, response.imageUrl)
    }

    @Test
    fun `get returns inactive product within branch`() {
        val productId = UUID.randomUUID()
        val product = MerchantProduct(
            id = productId,
            merchantApplicationId = merchantId,
            name = "Archived Coffee",
            unitPrice = BigDecimal("10.00"),
            active = false,
            merchantBranchId = branchId
        )
        whenever(productRepository.findByIdAndMerchantBranchId(productId, branchId)).thenReturn(Optional.of(product))

        val response = service.get(merchantId, productId, branchId)

        assertEquals(false, response.active)
    }

    @Test
    fun `create fails when merchant does not exist`() {
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(false)
        val request = CreateProductRequest(name = "Coffee", branchId = branchId, unitPrice = BigDecimal("10.00"))

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, request)
        }
    }

    @Test
    fun `get throws when product not found`() {
        val productId = UUID.randomUUID()
        whenever(productRepository.findByIdAndMerchantBranchId(productId, branchId)).thenReturn(Optional.empty())

        assertFailsWith<NoSuchElementException> {
            service.get(merchantId, productId, branchId)
        }
    }

    @Test
    fun `deactivate throws when product already inactive`() {
        val productId = UUID.randomUUID()
        whenever(productRepository.findByIdAndActiveTrueAndMerchantBranchId(productId, branchId)).thenReturn(Optional.empty())

        assertFailsWith<NoSuchElementException> {
            service.deactivate(merchantId, productId, branchId)
        }
    }
}
