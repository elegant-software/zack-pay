package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.RenameProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.UpdateProductCategoryDurationRequest
import com.elegant.software.blitzpay.merchant.domain.MerchantProductCategory
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MerchantProductCategoryServiceTest {
    private val categoryRepository = mock<MerchantProductCategoryRepository>()
    private val productRepository = mock<MerchantProductRepository>()
    private val merchantApplicationRepository = mock<MerchantApplicationRepository>()
    private val service = MerchantProductCategoryService(
        categoryRepository,
        productRepository,
        merchantApplicationRepository
    )
    private val merchantId = UUID.randomUUID()

    @Test
    fun `create succeeds with valid name`() {
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantApplicationIdAndNameIgnoreCase(merchantId, "Drinks")).thenReturn(null)
        whenever(categoryRepository.save(any<MerchantProductCategory>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, CreateProductCategoryRequest(" Drinks "))

        assertEquals("Drinks", response.name)
    }

    @Test
    fun `create throws for blank name`() {
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, CreateProductCategoryRequest("   "))
        }
    }

    @Test
    fun `create throws for overlong name`() {
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, CreateProductCategoryRequest("x".repeat(101)))
        }
    }

    @Test
    fun `create throws for duplicate name ignoring case`() {
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantApplicationIdAndNameIgnoreCase(merchantId, "Drinks")).thenReturn(
            category(name = "drinks")
        )

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, CreateProductCategoryRequest("Drinks"))
        }
    }

    @Test
    fun `list returns categories sorted alphabetically ignoring case`() {
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findAllByMerchantApplicationId(merchantId)).thenReturn(
            listOf(category(name = "vegetables"), category(name = "Drinks"), category(name = "bakery"))
        )

        val response = service.list(merchantId)

        assertEquals(listOf("bakery", "Drinks", "vegetables"), response.map { it.name })
    }

    @Test
    fun `rename succeeds`() {
        val existing = category(name = "Drinks")
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(categoryRepository.findByMerchantApplicationIdAndNameIgnoreCase(merchantId, "Soft Drinks")).thenReturn(null)
        whenever(categoryRepository.save(existing)).thenReturn(existing)

        val response = service.rename(merchantId, existing.id, RenameProductCategoryRequest("Soft Drinks"))

        assertEquals("Soft Drinks", response.name)
    }

    @Test
    fun `rename throws for missing category`() {
        val categoryId = UUID.randomUUID()
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, categoryId)).thenReturn(null)

        assertFailsWith<NoSuchElementException> {
            service.rename(merchantId, categoryId, RenameProductCategoryRequest("Soft Drinks"))
        }
    }

    @Test
    fun `rename throws for duplicate target name`() {
        val existing = category(name = "Drinks")
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(categoryRepository.findByMerchantApplicationIdAndNameIgnoreCase(merchantId, "Wine")).thenReturn(
            category(name = "Wine")
        )

        assertFailsWith<IllegalArgumentException> {
            service.rename(merchantId, existing.id, RenameProductCategoryRequest("Wine"))
        }
    }

    @Test
    fun `updateDuration succeeds with valid duration`() {
        val existing = category(name = "Haircut")
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(categoryRepository.save(existing)).thenReturn(existing)

        val response = service.updateDuration(merchantId, existing.id, UpdateProductCategoryDurationRequest(30))

        assertEquals(30, response.estimatedDurationMinutes)
    }

    @Test
    fun `updateDuration succeeds with null duration`() {
        val existing = category(name = "Haircut", estimatedDurationMinutes = 30)
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(categoryRepository.save(existing)).thenReturn(existing)

        val response = service.updateDuration(merchantId, existing.id, UpdateProductCategoryDurationRequest(null))

        assertEquals(null, response.estimatedDurationMinutes)
    }

    @Test
    fun `updateDuration throws for invalid duration`() {
        val existing = category(name = "Haircut")
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, existing.id)).thenReturn(existing)

        assertFailsWith<IllegalArgumentException> {
            service.updateDuration(merchantId, existing.id, UpdateProductCategoryDurationRequest(0))
        }
    }

    @Test
    fun `updateDuration throws for duration exceeding limit`() {
        val existing = category(name = "Haircut")
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, existing.id)).thenReturn(existing)

        assertFailsWith<IllegalArgumentException> {
            service.updateDuration(merchantId, existing.id, UpdateProductCategoryDurationRequest(481))
        }
    }

    @Test
    fun `delete succeeds when no active products assigned`() {
        val existing = category(name = "Drinks")
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(productRepository.countByProductCategoryIdAndActiveTrue(existing.id)).thenReturn(0)

        service.delete(merchantId, existing.id)

        verify(categoryRepository).deleteById(existing.id)
    }

    @Test
    fun `delete throws when active products assigned`() {
        val existing = category(name = "Drinks")
        whenever(merchantApplicationRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantApplicationIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(productRepository.countByProductCategoryIdAndActiveTrue(existing.id)).thenReturn(2)

        assertFailsWith<IllegalStateException> {
            service.delete(merchantId, existing.id)
        }
        verify(categoryRepository, never()).deleteById(existing.id)
    }

    private fun category(
        id: UUID = UUID.randomUUID(),
        name: String,
        estimatedDurationMinutes: Int? = null,
        createdAt: Instant = Instant.now()
    ) = MerchantProductCategory(
        id = id,
        merchantApplicationId = merchantId,
        name = name,
        estimatedDurationMinutes = estimatedDurationMinutes,
        createdAt = createdAt,
        updatedAt = createdAt
    )
}
