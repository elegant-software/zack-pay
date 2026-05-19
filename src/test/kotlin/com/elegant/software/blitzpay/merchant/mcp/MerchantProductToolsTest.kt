package com.elegant.software.blitzpay.merchant.mcp

import com.elegant.software.blitzpay.merchant.api.BranchResponse
import com.elegant.software.blitzpay.merchant.api.BulkCategoryInput
import com.elegant.software.blitzpay.merchant.api.BulkProductInput
import com.elegant.software.blitzpay.merchant.api.CreateBranchRequest
import com.elegant.software.blitzpay.merchant.api.CreateProductCategoryRequest
import com.elegant.software.blitzpay.merchant.api.CreateProductRequest
import com.elegant.software.blitzpay.merchant.api.ProductCategoryResponse
import com.elegant.software.blitzpay.merchant.api.ProductResponse
import com.elegant.software.blitzpay.merchant.application.MerchantBranchService
import com.elegant.software.blitzpay.merchant.application.MerchantLogoService
import com.elegant.software.blitzpay.merchant.application.MerchantManagementService
import com.elegant.software.blitzpay.merchant.application.MerchantProductCategoryService
import com.elegant.software.blitzpay.merchant.application.MerchantProductService
import com.elegant.software.blitzpay.merchant.application.MerchantRegistrationService
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.PrimaryContact
import com.elegant.software.blitzpay.storage.StorageService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class MerchantProductToolsTest {

    private val merchantProductService = mock<MerchantProductService>()
    private val merchantBranchService = mock<MerchantBranchService>()
    private val merchantRegistrationService = mock<MerchantRegistrationService>()
    private val merchantManagementService = mock<MerchantManagementService>()
    private val merchantLogoService = mock<MerchantLogoService>()
    private val merchantProductCategoryService = mock<MerchantProductCategoryService>()
    private val storageService = mock<StorageService>()

    private val merchantTools = MerchantMcpTools(
        merchantRegistrationService = merchantRegistrationService,
        merchantManagementService = merchantManagementService,
        merchantLogoService = merchantLogoService,
        merchantBranchService = merchantBranchService,
        storageService = storageService,
    )

    private val categoryTools = CategoryMcpTools(
        merchantProductCategoryService = merchantProductCategoryService,
    )

    private val productTools = ProductMcpTools(
        merchantProductService = merchantProductService,
        merchantProductCategoryService = merchantProductCategoryService,
    )

    @Test
    fun `upsertMerchant updates existing merchant and does not register when found`() {
        val merchantId = UUID.randomUUID()
        val details = merchantDetailsResponse(merchantId, "Cafe Blue")
        whenever(merchantRegistrationService.findByName("Cafe Blue"))
            .thenReturn(merchant(merchantId, "Cafe Blue", "REG-001"))
        whenever(merchantManagementService.get(merchantId)).thenReturn(details)
        whenever(merchantManagementService.update(eq(merchantId), any())).thenReturn(details)
        whenever(merchantBranchService.findByNameIncludingInactive(merchantId, "Main Branch")).thenReturn(null)
        whenever(merchantBranchService.create(eq(merchantId), any<CreateBranchRequest>(), eq(false))).thenReturn(branchResponse(merchantId))

        val result = merchantTools.upsertMerchant(merchantName = "Cafe Blue")

        assertEquals(merchantId, result.applicationId)
        verify(merchantRegistrationService, never()).registerDraft(any())
        verify(merchantManagementService).update(eq(merchantId), any())
        verify(merchantBranchService).create(eq(merchantId), any<CreateBranchRequest>(), eq(false))
    }

    @Test
    fun `upsertMerchant creates merchant and default branch when merchant is missing`() {
        val merchantId = UUID.randomUUID()
        whenever(merchantRegistrationService.findByName("Fresh Mart")).thenReturn(null)
        whenever(merchantRegistrationService.registerDraft(any())).thenReturn(merchant(merchantId, "Fresh Mart", "REG-NEW"))
        whenever(merchantBranchService.findByNameIncludingInactive(merchantId, "Main Branch")).thenReturn(null)
        whenever(merchantBranchService.create(eq(merchantId), any<CreateBranchRequest>(), eq(false))).thenReturn(branchResponse(merchantId))
        whenever(merchantManagementService.get(merchantId)).thenReturn(merchantDetailsResponse(merchantId, "Fresh Mart"))

        val result = merchantTools.upsertMerchant(merchantName = "Fresh Mart")

        assertEquals(merchantId, result.applicationId)
        val registerCaptor = argumentCaptor<com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest>()
        verify(merchantRegistrationService).registerDraft(registerCaptor.capture())
        assertEquals("Fresh Mart", registerCaptor.firstValue.businessProfile.legalBusinessName)
        assertEquals("Main Branch", captureBranchName())
    }

    @Test
    fun `getOrCreateProductId accepts empty image inputs and creates product without image`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantProductService.findByNameIncludingInactive(merchantId, branchId, "Latte")).thenReturn(null)
        whenever(merchantProductService.create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false))).thenReturn(
            ProductResponse(
                productId = UUID.randomUUID(),
                branchId = branchId,
                name = "Latte",
                description = null,
                unitPrice = BigDecimal("3.50"),
                imageUrl = null,
                active = false,
                status = "INACTIVE",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        productTools.getOrCreateProductId(
            merchantId = merchantId.toString(),
            branchId = branchId.toString(),
            productName = "Latte",
            unitPrice = "3.50",
            imageBase64 = "   ",
            imageFilePath = "",
            imageContentType = "  "
        )

        verify(merchantProductService).create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false))
    }

    @Test
    fun `getCategoryIdByName returns id when found`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductCategoryService.findByName(merchantId, "Drinks")).thenReturn(
            ProductCategoryResponse(categoryId, "Drinks", null, Instant.now(), Instant.now())
        )

        val result = categoryTools.getCategoryIdByName(merchantId.toString(), "Drinks")

        assertEquals(categoryId.toString(), result)
    }

    @Test
    fun `getCategoryIdByName throws when not found`() {
        val merchantId = UUID.randomUUID()
        whenever(merchantProductCategoryService.findByName(merchantId, "Missing")).thenReturn(null)

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            categoryTools.getCategoryIdByName(merchantId.toString(), "Missing")
        }
    }

    @Test
    fun `getOrCreateCategoryId returns existing id`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductCategoryService.findByName(merchantId, "Drinks")).thenReturn(
            ProductCategoryResponse(categoryId, "Drinks", null, Instant.now(), Instant.now())
        )

        val result = categoryTools.getOrCreateCategoryId(merchantId.toString(), "Drinks")

        assertEquals(categoryId.toString(), result)
    }

    @Test
    fun `getOrCreateCategoryId creates category when absent`() {
        val merchantId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductCategoryService.findByName(merchantId, "Wine")).thenReturn(null)
        whenever(
            merchantProductCategoryService.create(eq(merchantId), any<CreateProductCategoryRequest>())
        ).thenReturn(ProductCategoryResponse(categoryId, "Wine", null, Instant.now(), Instant.now()))

        val result = categoryTools.getOrCreateCategoryId(merchantId.toString(), "Wine")

        assertEquals(categoryId.toString(), result)
        verify(merchantProductCategoryService).create(eq(merchantId), any<CreateProductCategoryRequest>())
    }

    @Test
    fun `listProductCategories delegates to service`() {
        val merchantId = UUID.randomUUID()
        val categories = listOf(ProductCategoryResponse(UUID.randomUUID(), "Snacks", null, Instant.now(), Instant.now()))
        whenever(merchantProductCategoryService.list(merchantId)).thenReturn(categories)

        val result = categoryTools.listProductCategories(merchantId.toString())

        assertEquals(categories, result)
    }

    @Test
    fun `updateProduct passes categoryId through to request`() {
        val merchantId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductService.updateIncludingInactive(eq(merchantId), eq(productId), any(), isNull())).thenReturn(
            ProductResponse(
                productId = productId,
                branchId = branchId,
                name = "Latte",
                description = null,
                unitPrice = BigDecimal("3.50"),
                imageUrl = null,
                active = true,
                status = "ACTIVE",
                categoryId = categoryId,
                categoryName = "Drinks",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        productTools.updateProduct(
            merchantId = merchantId.toString(),
            productId = productId.toString(),
            branchId = branchId.toString(),
            name = "Latte",
            unitPrice = "3.50",
            categoryId = categoryId.toString(),
            productCode = "12"
        )

        val captor = argumentCaptor<com.elegant.software.blitzpay.merchant.api.UpdateProductRequest>()
        verify(merchantProductService).updateIncludingInactive(eq(merchantId), eq(productId), captor.capture(), isNull())
        assertEquals(categoryId, captor.firstValue.categoryId)
        assertEquals(12L, captor.firstValue.productCode)
    }

    @Test
    fun `updateProduct uses inactive aware service path`() {
        val merchantId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantProductService.updateIncludingInactive(eq(merchantId), eq(productId), any(), isNull())).thenReturn(
            ProductResponse(
                productId = productId,
                branchId = branchId,
                name = "Latte",
                description = null,
                unitPrice = BigDecimal("3.50"),
                imageUrl = null,
                active = true,
                status = "ACTIVE",
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        productTools.updateProduct(
            merchantId = merchantId.toString(),
            productId = productId.toString(),
            branchId = branchId.toString(),
            name = "Latte",
            unitPrice = "3.50"
        )

        verify(merchantProductService).updateIncludingInactive(eq(merchantId), eq(productId), any(), isNull())
        verify(merchantProductService, never()).update(eq(merchantId), eq(productId), any(), isNull())
        verify(merchantProductService, never()).markInactive(any(), any())
    }

    @Test
    fun `getOrCreateProductId passes categoryId into create request`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductService.findByNameIncludingInactive(merchantId, branchId, "Latte")).thenReturn(null)
        whenever(merchantProductService.create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false))).thenReturn(
            ProductResponse(
                productId = UUID.randomUUID(),
                branchId = branchId,
                name = "Latte",
                description = null,
                unitPrice = BigDecimal("3.50"),
                imageUrl = null,
                active = false,
                status = "INACTIVE",
                categoryId = categoryId,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        productTools.getOrCreateProductId(
            merchantId = merchantId.toString(),
            branchId = branchId.toString(),
            productName = "Latte",
            unitPrice = "3.50",
            categoryId = categoryId.toString()
        )

        val captor = argumentCaptor<CreateProductRequest>()
        verify(merchantProductService).create(eq(merchantId), captor.capture(), isNull(), eq(false))
        assertEquals(categoryId, captor.firstValue.categoryId)
    }

    @Test
    fun `getOrCreateProductId passes product code into create request`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantProductService.findByNameIncludingInactive(merchantId, branchId, "Latte")).thenReturn(null)
        whenever(merchantProductService.create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false))).thenReturn(
            ProductResponse(
                productId = UUID.randomUUID(),
                branchId = branchId,
                name = "Latte",
                description = null,
                unitPrice = BigDecimal("3.50"),
                imageUrl = null,
                active = false,
                status = "INACTIVE",
                productCode = 12L,
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )

        productTools.getOrCreateProductId(
            merchantId = merchantId.toString(),
            branchId = branchId.toString(),
            productName = "Latte",
            unitPrice = "3.50",
            productCode = "12"
        )

        val captor = argumentCaptor<CreateProductRequest>()
        verify(merchantProductService).create(eq(merchantId), captor.capture(), isNull(), eq(false))
        assertEquals(12L, captor.firstValue.productCode)
    }

    @Test
    fun `bulkUpsertProducts creates all items when none exist`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        val products = listOf(
            BulkProductInput(productName = "Latte", unitPrice = "3.50"),
            BulkProductInput(productName = "Mocha", unitPrice = "4.20"),
            BulkProductInput(productName = "Espresso", unitPrice = "2.80"),
        )
        products.forEach { input ->
            whenever(merchantProductService.findByNameIncludingInactive(merchantId, branchId, input.productName)).thenReturn(null)
        }
        whenever(merchantProductService.create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false)))
            .thenAnswer { invocation ->
                val request = invocation.getArgument<CreateProductRequest>(1)
                productResponse(branchId = branchId, name = request.name, unitPrice = request.unitPrice)
            }

        val result = productTools.bulkUpsertProducts(merchantId.toString(), branchId.toString(), products)

        assertEquals(3, result.created.size)
        assertEquals(emptyList<Any>(), result.skipped)
        assertEquals(emptyList<Any>(), result.failed)
    }

    @Test
    fun `bulkUpsertProducts updates products that already exist by name`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        val existingId = UUID.randomUUID()
        val updatedResponse = productResponse(productId = existingId, branchId = branchId, name = "Latte", unitPrice = BigDecimal("3.99"))
        whenever(merchantProductService.findByNameIncludingInactive(merchantId, branchId, "Latte"))
            .thenReturn(productResponse(productId = existingId, branchId = branchId, name = "Latte", unitPrice = BigDecimal("3.50")))
        whenever(merchantProductService.findByNameIncludingInactive(merchantId, branchId, "Mocha")).thenReturn(null)
        whenever(merchantProductService.updateIncludingInactive(eq(merchantId), eq(existingId), any(), isNull()))
            .thenReturn(updatedResponse)
        whenever(merchantProductService.create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false)))
            .thenReturn(productResponse(branchId = branchId, name = "Mocha", unitPrice = BigDecimal("4.20")))

        val result = productTools.bulkUpsertProducts(
            merchantId = merchantId.toString(),
            branchId = branchId.toString(),
            products = listOf(
                BulkProductInput(productName = "Latte", unitPrice = "3.99"),
                BulkProductInput(productName = "Mocha", unitPrice = "4.20"),
            )
        )

        assertEquals(1, result.created.size)
        assertEquals(1, result.updated.size)
        assertEquals(existingId, result.updated.single().productId)
        assertEquals(emptyList<Any>(), result.skipped)
    }

    @Test
    fun `bulkUpsertProducts skips within-batch name duplicates`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantProductService.findByNameIncludingInactive(merchantId, branchId, "Latte")).thenReturn(null)
        whenever(merchantProductService.create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false)))
            .thenReturn(productResponse(branchId = branchId, name = "Latte", unitPrice = BigDecimal("3.50")))

        val result = productTools.bulkUpsertProducts(
            merchantId = merchantId.toString(),
            branchId = branchId.toString(),
            products = listOf(
                BulkProductInput(productName = "Latte", unitPrice = "3.50"),
                BulkProductInput(productName = "latte", unitPrice = "3.75"),
            )
        )

        assertEquals(1, result.created.size)
        assertEquals(1, result.skipped.size)
        assertEquals("duplicate within batch", result.skipped.single().reason)
        verify(merchantProductService, times(1)).findByNameIncludingInactive(merchantId, branchId, "Latte")
        verify(merchantProductService, times(1)).create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false))
    }

    @Test
    fun `bulkUpsertProducts rejects entire batch when size exceeds 200`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            productTools.bulkUpsertProducts(
                merchantId = merchantId.toString(),
                branchId = branchId.toString(),
                products = (1..201).map { BulkProductInput(productName = "Product $it", unitPrice = "1.00") }
            )
        }

        verify(merchantProductService, never()).findByNameIncludingInactive(any(), any(), any())
        verify(merchantProductService, never()).create(any(), any<CreateProductRequest>(), any(), any())
    }

    @Test
    fun `bulkUpsertProducts puts item in failed list when service throws`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantProductService.findByNameIncludingInactive(merchantId, branchId, "Broken")).thenReturn(null)
        whenever(merchantProductService.findByNameIncludingInactive(merchantId, branchId, "Latte")).thenReturn(null)
        whenever(merchantProductService.create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false)))
            .thenAnswer { invocation ->
                val request = invocation.getArgument<CreateProductRequest>(1)
                if (request.name == "Broken") {
                    throw IllegalArgumentException("unitPrice must be >= 0")
                }
                productResponse(branchId = branchId, name = request.name, unitPrice = request.unitPrice)
            }

        val result = productTools.bulkUpsertProducts(
            merchantId = merchantId.toString(),
            branchId = branchId.toString(),
            products = listOf(
                BulkProductInput(productName = "Broken", unitPrice = "-1.00"),
                BulkProductInput(productName = "Latte", unitPrice = "3.50"),
            )
        )

        assertEquals(1, result.created.size)
        assertEquals(1, result.failed.size)
        assertEquals("Broken", result.failed.single().name)
        assertEquals("unitPrice must be >= 0", result.failed.single().reason)
    }

    @Test
    fun `bulkUpsertProducts passes categoryId through to create request`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantProductService.findByNameIncludingInactive(merchantId, branchId, "Latte")).thenReturn(null)
        whenever(merchantProductService.create(eq(merchantId), any<CreateProductRequest>(), isNull(), eq(false)))
            .thenReturn(
                productResponse(
                    branchId = branchId,
                    name = "Latte",
                    unitPrice = BigDecimal("3.50"),
                    categoryId = categoryId
                )
            )

        productTools.bulkUpsertProducts(
            merchantId = merchantId.toString(),
            branchId = branchId.toString(),
            products = listOf(BulkProductInput(productName = "Latte", unitPrice = "3.50", categoryId = categoryId.toString()))
        )

        val captor = argumentCaptor<CreateProductRequest>()
        verify(merchantProductService).create(eq(merchantId), captor.capture(), isNull(), eq(false))
        assertEquals(categoryId, captor.firstValue.categoryId)
    }

    @Test
    fun `bulkCreateCategories creates all when none exist`() {
        val merchantId = UUID.randomUUID()
        val categories = listOf(
            BulkCategoryInput("Starters"),
            BulkCategoryInput("Mains"),
            BulkCategoryInput("Desserts"),
        )
        categories.forEach { input ->
            whenever(merchantProductCategoryService.findByName(merchantId, input.categoryName)).thenReturn(null)
        }
        whenever(merchantProductCategoryService.create(eq(merchantId), any<CreateProductCategoryRequest>()))
            .thenAnswer { invocation ->
                val request = invocation.getArgument<CreateProductCategoryRequest>(1)
                categoryResponse(name = request.name)
            }

        val result = categoryTools.bulkCreateCategories(merchantId.toString(), categories)

        assertEquals(3, result.created.size)
        assertEquals(emptyList<Any>(), result.skipped)
        assertEquals(emptyList<Any>(), result.failed)
    }

    @Test
    fun `bulkCreateCategories skips categories that already exist for merchant`() {
        val merchantId = UUID.randomUUID()
        val existingId = UUID.randomUUID()
        whenever(merchantProductCategoryService.findByName(merchantId, "Starters"))
            .thenReturn(categoryResponse(id = existingId, name = "Starters"))
        whenever(merchantProductCategoryService.findByName(merchantId, "Mains")).thenReturn(null)
        whenever(merchantProductCategoryService.create(eq(merchantId), any<CreateProductCategoryRequest>()))
            .thenReturn(categoryResponse(name = "Mains"))

        val result = categoryTools.bulkCreateCategories(
            merchantId = merchantId.toString(),
            categories = listOf(BulkCategoryInput("Starters"), BulkCategoryInput("Mains"))
        )

        assertEquals(1, result.created.size)
        assertEquals(1, result.skipped.size)
        assertEquals("already exists", result.skipped.single().reason)
        assertEquals(existingId.toString(), result.skipped.single().existingId)
    }

    @Test
    fun `bulkCreateCategories skips within-batch duplicates case-insensitively`() {
        val merchantId = UUID.randomUUID()
        whenever(merchantProductCategoryService.findByName(merchantId, "Starters")).thenReturn(null)
        whenever(merchantProductCategoryService.create(eq(merchantId), any<CreateProductCategoryRequest>()))
            .thenReturn(categoryResponse(name = "Starters"))

        val result = categoryTools.bulkCreateCategories(
            merchantId = merchantId.toString(),
            categories = listOf(BulkCategoryInput("Starters"), BulkCategoryInput("starters"))
        )

        assertEquals(1, result.created.size)
        assertEquals(1, result.skipped.size)
        assertEquals("duplicate within batch", result.skipped.single().reason)
        verify(merchantProductCategoryService, times(1)).findByName(merchantId, "Starters")
    }

    @Test
    fun `bulkCreateCategories rejects entire batch when size exceeds 200`() {
        val merchantId = UUID.randomUUID()

        kotlin.test.assertFailsWith<IllegalArgumentException> {
            categoryTools.bulkCreateCategories(
                merchantId = merchantId.toString(),
                categories = (1..201).map { BulkCategoryInput("Category $it") }
            )
        }

        verify(merchantProductCategoryService, never()).findByName(any(), any())
        verify(merchantProductCategoryService, never()).create(any(), any<CreateProductCategoryRequest>())
    }

    @Test
    fun `bulkCreateCategories puts item in failed list when service throws`() {
        val merchantId = UUID.randomUUID()
        whenever(merchantProductCategoryService.findByName(merchantId, "Broken")).thenReturn(null)
        whenever(merchantProductCategoryService.findByName(merchantId, "Mains")).thenReturn(null)
        whenever(merchantProductCategoryService.create(eq(merchantId), any<CreateProductCategoryRequest>()))
            .thenAnswer { invocation ->
                val request = invocation.getArgument<CreateProductCategoryRequest>(1)
                if (request.name == "Broken") {
                    throw IllegalArgumentException("Category name must not be blank")
                }
                categoryResponse(name = request.name)
            }

        val result = categoryTools.bulkCreateCategories(
            merchantId = merchantId.toString(),
            categories = listOf(BulkCategoryInput("Broken"), BulkCategoryInput("Mains"))
        )

        assertEquals(1, result.created.size)
        assertEquals(1, result.failed.size)
        assertEquals("Broken", result.failed.single().name)
        assertEquals("Category name must not be blank", result.failed.single().reason)
    }

    private fun captureBranchName(): String {
        val requestCaptor = argumentCaptor<CreateBranchRequest>()
        verify(merchantBranchService).create(any(), requestCaptor.capture(), eq(false))
        return requestCaptor.firstValue.name
    }

    private fun merchantDetailsResponse(id: UUID, name: String): com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse =
        com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse(
            applicationId = id,
            applicationReference = "BLTZ-TEST1234",
            registrationNumber = "REG-001",
            businessType = "RETAIL",
            operatingCountry = "US",
            legalBusinessName = name,
            primaryBusinessAddress = "Street 1",
            contactFullName = "Owner",
            contactEmail = "owner@example.com",
            contactPhoneNumber = "000",
            activePaymentChannels = emptySet(),
            status = MerchantOnboardingStatus.DRAFT,
            submittedAt = null,
            lastUpdatedAt = Instant.now(),
        )

    private fun merchant(id: UUID, name: String, registrationNumber: String): MerchantApplication =
        MerchantApplication(
            id = id,
            applicationReference = "BLTZ-TEST1234",
            businessProfile = BusinessProfile(
                legalBusinessName = name,
                businessType = "RETAIL",
                registrationNumber = registrationNumber,
                operatingCountry = "US",
                primaryBusinessAddress = "Street 1"
            ),
            primaryContact = PrimaryContact(
                fullName = "Owner",
                email = "owner@example.com",
                phoneNumber = "000"
            ),
            status = MerchantOnboardingStatus.DRAFT
        )

    private fun branchResponse(merchantId: UUID): BranchResponse =
        BranchResponse(
            id = UUID.randomUUID(),
            merchantId = merchantId,
            name = "Main Branch",
            active = true,
            addressLine1 = null,
            addressLine2 = null,
            city = null,
            postalCode = null,
            country = null,
            contactFullName = null,
            contactEmail = null,
            contactPhoneNumber = null,
            activePaymentChannels = emptySet(),
            latitude = null,
            longitude = null,
            geofenceRadiusMeters = null,
            googlePlaceId = null,
            placeFormattedAddress = null,
            placeRating = null,
            placeReviewCount = null,
            placeEnrichmentStatus = null,
            placeEnrichedAt = null,
            imageUrl = null,
            status = "INACTIVE",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

    private fun productResponse(
        productId: UUID = UUID.randomUUID(),
        branchId: UUID,
        name: String,
        unitPrice: BigDecimal,
        categoryId: UUID? = null,
    ): ProductResponse =
        ProductResponse(
            productId = productId,
            branchId = branchId,
            name = name,
            description = null,
            unitPrice = unitPrice,
            imageUrl = null,
            active = false,
            status = "INACTIVE",
            categoryId = categoryId,
            categoryName = null,
            productCode = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

    private fun categoryResponse(
        id: UUID = UUID.randomUUID(),
        name: String,
    ): ProductCategoryResponse =
        ProductCategoryResponse(
            id = id,
            name = name,
            estimatedDurationMinutes = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
}
