package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.model.PostalAddress
import com.elegant.software.blitzpay.storage.PresignedUpload
import jakarta.persistence.Query
import org.hibernate.Filter
import org.hibernate.Session
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.math.BigDecimal

class MerchantContractTest : ContractVerifierBase() {
    private val session = mock<Session>()
    private val hibernateFilter = mock<Filter>()
    private val nativeQuery = mock<Query>()

    @BeforeEach
    fun setupMerchantContracts() {
        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.enableFilter(any())).thenReturn(hibernateFilter)
        whenever(hibernateFilter.setParameter(any<String>(), any())).thenReturn(hibernateFilter)
        whenever(entityManager.createNativeQuery(any<String>())).thenReturn(nativeQuery)
        whenever(nativeQuery.setParameter(any<String>(), any())).thenReturn(nativeQuery)
        whenever(nativeQuery.singleResult).thenReturn("contract")
        whenever(storageService.presignDownload(any(), any())).thenAnswer { "https://signed.example/${it.arguments[0]}" }
        whenever(storageService.presignUpload(any(), any(), any())).thenAnswer {
            PresignedUpload(
                storageKey = it.arguments[0] as String,
                uploadUrl = "https://upload.example/${it.arguments[0]}",
                expiresAt = Instant.parse("2026-04-24T18:45:00Z")
            )
        }
    }

    @Test
    fun `POST merchants returns 201 and ACTIVE status for valid registration`() {
        whenever(merchantRepository.save(any<Merchant>())).thenAnswer { it.arguments[0] }

        webTestClient.post()
            .uri("/v1/merchants")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "merchantName": "Acme GmbH",
                  "businessType": "RETAIL",
                  "operatingCountry": "DE"
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.merchantName").isEqualTo("Acme GmbH")
            .jsonPath("$.merchantStatus").isEqualTo("ACTIVE")
            .jsonPath("$.merchantId").exists()
            .jsonPath("$.merchantCode").exists()
    }

    @Test
    fun `GET merchant by id returns 200 when found`() {
        val merchant = Merchant(
            merchantName = "Test GmbH",
            businessType = "RETAIL",
            operatingCountry = "DE",
            logoStorageKey = "merchants/logo/test.webp",
            publicEmail = "test@test.de",
        )
        whenever(merchantRepository.findById(merchant.id))
            .thenReturn(Optional.of(merchant))

        webTestClient.get()
            .uri("/v1/merchants/${merchant.id}")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.merchantId").isEqualTo(merchant.id.toString())
            .jsonPath("$.merchantName").isEqualTo("Test GmbH")
            .jsonPath("$.logoUrl").isEqualTo("https://signed.example/merchants/logo/test.webp")
    }

    @Test
    fun `GET merchant by id returns 404 when not found`() {
        val unknownId = UUID.randomUUID()
        whenever(merchantRepository.findById(unknownId))
            .thenReturn(Optional.empty())

        webTestClient.get()
            .uri("/v1/merchants/$unknownId")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `PUT merchant updates editable fields and status`() {
        val merchant = Merchant(
            merchantName = "Before GmbH",
            businessType = "RETAIL",
            operatingCountry = "DE",
        )
        whenever(merchantRepository.findById(merchant.id))
            .thenReturn(Optional.of(merchant))
        whenever(merchantRepository.save(any<Merchant>()))
            .thenAnswer { it.arguments[0] }

        webTestClient.put()
            .uri("/v1/merchants/${merchant.id}")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "merchantName": "After GmbH",
                  "merchantStatus": "INACTIVE",
                  "contactInfo": {
                    "website": "https://after.example.com",
                    "email": "after@test.de",
                    "phoneNumber": "+49309999999"
                  },
                  "address": {
                    "addressLine1": "New Strasse 9",
                    "city": "Berlin",
                    "postalCode": "10115",
                    "country": "DE"
                  },
                  "location": {
                    "latitude": 52.52,
                    "longitude": 13.405,
                    "geofenceRadiusMeters": 150
                  },
                  "offerings": ["PRE_ORDER", "DEFERRED_PAYMENT"]
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.merchantName").isEqualTo("After GmbH")
            .jsonPath("$.merchantStatus").isEqualTo("INACTIVE")
            .jsonPath("$.contactInfo.email").isEqualTo("after@test.de")
            .jsonPath("$.address.addressLine1").isEqualTo("New Strasse 9")
    }

    @Test
    fun `POST merchant logo upload url returns presigned upload details`() {
        val merchant = Merchant(merchantName = "Logo Merchant", businessType = "RETAIL", operatingCountry = "DE")
        whenever(merchantRepository.findById(merchant.id)).thenReturn(Optional.of(merchant))

        webTestClient.post()
            .uri("/v1/merchants/${merchant.id}/logo/upload-url")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"contentType":"image/webp"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.storageKey").isEqualTo("merchants/${merchant.id}/logo.webp")
            .jsonPath("$.uploadUrl").isEqualTo("https://upload.example/merchants/${merchant.id}/logo.webp")
            .jsonPath("$.expiresAt").exists()
    }

    @Test
    fun `PUT merchant logo stores canonical storage key`() {
        val merchant = Merchant(merchantName = "Logo Merchant", businessType = "RETAIL", operatingCountry = "DE")
        whenever(merchantRepository.findById(merchant.id)).thenReturn(Optional.of(merchant))
        whenever(merchantRepository.save(any<Merchant>())).thenAnswer { it.arguments[0] }

        webTestClient.put()
            .uri("/v1/merchants/${merchant.id}/logo")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"storageKey":"merchants/${merchant.id}/logo.webp"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.logoStorageKey").isEqualTo("merchants/${merchant.id}/logo.webp")
    }

    @Test
    fun `PUT branch updates branch details and contact`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantRepository.findById(merchantId)).thenReturn(
            Optional.of(Merchant(id = merchantId, merchantName = "Test Merchant", businessType = "RETAIL", operatingCountry = "DE"))
        )
        whenever(merchantBranchRepository.findById(branchId)).thenReturn(
            Optional.of(
                com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch(
                    id = branchId,
                    merchantId = merchantId,
                    name = "Old Branch",
                    address = PostalAddress(
                        addressLine1 = "Old Street 1",
                        city = "Berlin",
                        postalCode = "10115",
                        country = "DE",
                    ),
                ).also {
                    it.location = com.elegant.software.blitzpay.merchant.persistence.model.MerchantLocation(
                        latitude = 52.52,
                        longitude = 13.405,
                        geofenceRadiusMeters = 200
                    )
                }
            )
        )
        whenever(merchantBranchRepository.saveAndFlush(any<com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch>()))
            .thenAnswer { it.arguments[0] }

        webTestClient.put()
            .uri("/v1/merchants/$merchantId/branches/$branchId")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {
                  "name": "Updated Branch",
                  "active": true,
                  "addressLine1": "Main Street 1",
                  "city": "Berlin",
                  "postalCode": "10115",
                  "country": "DE",
                  "contactFullName": "Store Manager",
                  "website": "https://branch.example.com",
                  "contactEmail": "branch@test.de",
                  "contactPhoneNumber": "+49305555555",
                  "latitude": 52.52,
                  "longitude": 13.405,
                  "geofenceRadiusMeters": 200
                }
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("Updated Branch")
            .jsonPath("$.contactFullName").isEqualTo("Store Manager")
            .jsonPath("$.website").isEqualTo("https://branch.example.com")
            .jsonPath("$.contactEmail").isEqualTo("branch@test.de")
    }

    @Test
    fun `PUT branch image stores storage key and returns signed image url`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(merchantBranchRepository.findById(branchId)).thenReturn(
            Optional.of(
                com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch(
                    id = branchId,
                    merchantId = merchantId,
                    name = "Image Branch"
                )
            )
        )
        whenever(merchantBranchRepository.save(any<com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch>()))
            .thenAnswer { it.arguments[0] }

        webTestClient.put()
            .uri("/v1/merchants/$merchantId/branches/$branchId/image")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"storageKey":"merchants/$merchantId/branches/$branchId/image.webp"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.imageUrl").isEqualTo("https://signed.example/merchants/$merchantId/branches/$branchId/image.webp")
    }

    @Test
    fun `POST product accepts multipart product fields and image`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(merchantBranchRepository.existsByMerchantIdAndId(merchantId, branchId)).thenReturn(true)
        whenever(merchantBranchRepository.existsByMerchantIdAndIdAndActiveTrue(merchantId, branchId)).thenReturn(true)
        whenever(merchantProductCategoryRepository.existsByIdAndMerchantId(categoryId, merchantId)).thenReturn(true)
        whenever(merchantProductCategoryRepository.findByMerchantIdAndId(merchantId, categoryId)).thenReturn(
            com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategory(
                id = categoryId,
                merchantId = merchantId,
                name = "Drinks"
            )
        )
        whenever(merchantProductRepository.save(any<MerchantProduct>())).thenAnswer { it.arguments[0] }
        val multipart = MultipartBodyBuilder().apply {
            part("name", "Coffee Blend")
            part("branchId", branchId.toString())
            part("description", "**Medium roast**")
            part("unitPrice", "12.50")
            part("categoryId", categoryId.toString())
            part("productCode", "12")
            part("image", byteArrayOf(1, 2, 3))
                .filename("coffee.webp")
                .contentType(MediaType("image", "webp"))
        }

        webTestClient.post()
            .uri("/v1/merchants/$merchantId/products")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(multipart.build())
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.name").isEqualTo("Coffee Blend")
            .jsonPath("$.description").isEqualTo("**Medium roast**")
            .jsonPath("$.categoryId").isEqualTo(categoryId.toString())
            .jsonPath("$.categoryName").isEqualTo("Drinks")
            .jsonPath("$.productCode").isEqualTo(12)
            .jsonPath("$.imageUrl").exists()
    }

    @Test
    fun `GET products returns signed image url and description`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        val categoryId = UUID.randomUUID()
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.enableFilter(any())).thenReturn(hibernateFilter)
        whenever(hibernateFilter.setParameter(any<String>(), any())).thenReturn(hibernateFilter)
        whenever(entityManager.createNativeQuery(any<String>())).thenReturn(nativeQuery)
        whenever(nativeQuery.setParameter(any<String>(), any())).thenReturn(nativeQuery)
        whenever(nativeQuery.singleResult).thenReturn(merchantId.toString())
        whenever(merchantProductCategoryRepository.findByMerchantIdAndId(merchantId, categoryId)).thenReturn(
            com.elegant.software.blitzpay.merchant.persistence.model.MerchantProductCategory(
                id = categoryId,
                merchantId = merchantId,
                name = "Drinks"
            )
        )
        whenever(merchantProductRepository.findAllByMerchantBranchId(branchId)).thenReturn(
            listOf(
                MerchantProduct(
                    merchantId = merchantId,
                    merchantBranchId = branchId,
                    name = "Coffee Blend",
                    description = "**Medium roast**",
                    unitPrice = BigDecimal("12.50"),
                    imageStorageKey = "merchants/$merchantId/branches/$branchId/products/product/image.webp",
                    productCategoryId = categoryId,
                    productCode = 12L
                )
            )
        )

        webTestClient.get()
            .uri("/v1/merchants/$merchantId/products?branchId=$branchId")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[0].description").isEqualTo("**Medium roast**")
            .jsonPath("$[0].categoryId").isEqualTo(categoryId.toString())
            .jsonPath("$[0].categoryName").isEqualTo("Drinks")
            .jsonPath("$[0].productCode").isEqualTo(12)
            .jsonPath("$[0].imageUrl").isEqualTo("https://signed.example/merchants/$merchantId/branches/$branchId/products/product/image.webp")
    }

    @Test
    fun `POST product rejects unsupported image type`() {
        val merchantId = UUID.randomUUID()
        val branchId = UUID.randomUUID()
        whenever(merchantRepository.existsById(merchantId)).thenReturn(true)
        val multipart = MultipartBodyBuilder().apply {
            part("name", "Coffee Blend")
            part("branchId", branchId.toString())
            part("unitPrice", "12.50")
            part("image", "not an image".toByteArray())
                .filename("notes.txt")
                .contentType(MediaType.TEXT_PLAIN)
        }

        webTestClient.post()
            .uri("/v1/merchants/$merchantId/products")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .bodyValue(multipart.build())
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }
}
