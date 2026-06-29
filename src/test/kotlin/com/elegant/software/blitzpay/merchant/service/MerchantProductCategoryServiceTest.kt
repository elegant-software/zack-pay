package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.UpdateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategory
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantOfferingAssignmentRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
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
    private val merchantRepository = mock<MerchantRepository>()
    private val offeringAssignmentRepository = mock<MerchantOfferingAssignmentRepository>()
    private val service = MerchantProductCategoryService(
        categoryRepository,
        productRepository,
        merchantRepository,
        offeringAssignmentRepository,
    )
    private val merchantId = UUID.randomUUID()

    @Test
    fun `create succeeds with valid name`() {
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, "Drinks")).thenReturn(null)
        whenever(categoryRepository.save(any<MerchantProductCategory>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, CreateProductCategoryRequest(" Drinks "))

        assertEquals("Drinks", response.name)
    }

    @Test
    fun `create throws for blank name`() {
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, CreateProductCategoryRequest("   "))
        }
    }

    @Test
    fun `create throws for overlong name`() {
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, CreateProductCategoryRequest("x".repeat(101)))
        }
    }

    @Test
    fun `create with duration throws when appointment booking not enabled`() {
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, "Haircut")).thenReturn(null)
        whenever(offeringAssignmentRepository.existsByMerchantIdAndOfferingCode(merchantId, "APPOINTMENT_BOOKING")).thenReturn(false)

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, CreateProductCategoryRequest("Haircut", estimatedDurationMinutes = 30))
        }
    }

    @Test
    fun `create with duration succeeds when appointment booking enabled`() {
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, "Haircut")).thenReturn(null)
        whenever(offeringAssignmentRepository.existsByMerchantIdAndOfferingCode(merchantId, "APPOINTMENT_BOOKING")).thenReturn(true)
        whenever(categoryRepository.save(any<MerchantProductCategory>())).thenAnswer { it.arguments[0] }

        val response = service.create(merchantId, CreateProductCategoryRequest("Haircut", estimatedDurationMinutes = 30))

        assertEquals(30, response.estimatedDurationMinutes)
    }

    @Test
    fun `create throws for duplicate name ignoring case`() {
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, "Drinks")).thenReturn(
            category(name = "drinks")
        )

        assertFailsWith<IllegalArgumentException> {
            service.create(merchantId, CreateProductCategoryRequest("Drinks"))
        }
    }

    @Test
    fun `list returns categories sorted alphabetically ignoring case`() {
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findAllByMerchantId(merchantId)).thenReturn(
            listOf(category(name = "vegetables"), category(name = "Drinks"), category(name = "bakery"))
        )

        val response = service.list(merchantId)

        assertEquals(listOf("bakery", "Drinks", "vegetables"), response.map { it.name })
    }

    @Test
    fun `update succeeds with new name`() {
        val existing = category(name = "Drinks")
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, "Soft Drinks")).thenReturn(null)
        whenever(categoryRepository.save(existing)).thenReturn(existing)

        val response = service.update(merchantId, existing.id, UpdateProductCategoryRequest("Soft Drinks"))

        assertEquals("Soft Drinks", response.name)
    }

    @Test
    fun `update throws for missing category`() {
        val categoryId = UUID.randomUUID()
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndId(merchantId, categoryId)).thenReturn(null)

        assertFailsWith<NoSuchElementException> {
            service.update(merchantId, categoryId, UpdateProductCategoryRequest("Soft Drinks"))
        }
    }

    @Test
    fun `update throws for duplicate target name`() {
        val existing = category(name = "Drinks")
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, "Wine")).thenReturn(
            category(name = "Wine")
        )

        assertFailsWith<IllegalArgumentException> {
            service.update(merchantId, existing.id, UpdateProductCategoryRequest("Wine"))
        }
    }

    @Test
    fun `update with duration succeeds when appointment booking enabled`() {
        val existing = category(name = "Haircut")
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, "Haircut")).thenReturn(existing)
        whenever(offeringAssignmentRepository.existsByMerchantIdAndOfferingCode(merchantId, "APPOINTMENT_BOOKING")).thenReturn(true)
        whenever(categoryRepository.save(existing)).thenReturn(existing)

        val response = service.update(merchantId, existing.id, UpdateProductCategoryRequest("Haircut", estimatedDurationMinutes = 30))

        assertEquals(30, response.estimatedDurationMinutes)
    }

    @Test
    fun `update with duration throws when appointment booking not enabled`() {
        val existing = category(name = "Haircut")
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, "Haircut")).thenReturn(existing)
        whenever(offeringAssignmentRepository.existsByMerchantIdAndOfferingCode(merchantId, "APPOINTMENT_BOOKING")).thenReturn(false)

        assertFailsWith<IllegalArgumentException> {
            service.update(merchantId, existing.id, UpdateProductCategoryRequest("Haircut", estimatedDurationMinutes = 30))
        }
    }

    @Test
    fun `update with null duration clears duration without offering check`() {
        val existing = category(name = "Haircut", estimatedDurationMinutes = 30)
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, "Haircut")).thenReturn(existing)
        whenever(categoryRepository.save(existing)).thenReturn(existing)

        val response = service.update(merchantId, existing.id, UpdateProductCategoryRequest("Haircut", estimatedDurationMinutes = null))

        assertEquals(null, response.estimatedDurationMinutes)
    }

    @Test
    fun `update with invalid duration throws`() {
        val existing = category(name = "Haircut")
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(categoryRepository.findByMerchantIdAndNameIgnoreCase(merchantId, "Haircut")).thenReturn(existing)
        whenever(offeringAssignmentRepository.existsByMerchantIdAndOfferingCode(merchantId, "APPOINTMENT_BOOKING")).thenReturn(true)

        assertFailsWith<IllegalArgumentException> {
            service.update(merchantId, existing.id, UpdateProductCategoryRequest("Haircut", estimatedDurationMinutes = 481))
        }
    }

    @Test
    fun `delete succeeds when no active products assigned`() {
        val existing = category(name = "Drinks")
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndId(merchantId, existing.id)).thenReturn(existing)
        whenever(productRepository.countByProductCategoryIdAndActiveTrue(existing.id)).thenReturn(0)

        service.delete(merchantId, existing.id)

        verify(categoryRepository).deleteById(existing.id)
    }

    @Test
    fun `delete throws when active products assigned`() {
        val existing = category(name = "Drinks")
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(categoryRepository.findByMerchantIdAndId(merchantId, existing.id)).thenReturn(existing)
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
        merchantId = merchantId,
        name = name,
        estimatedDurationMinutes = estimatedDurationMinutes,
        createdAt = createdAt,
        updatedAt = createdAt
    )
}
