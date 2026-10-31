package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.HttpStatus
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * The public Web Storefront browses a merchant's catalog without a login.
 * Only the catalog reads it needs are anonymous; everything else still requires a token.
 */
class StorefrontPublicAccessContractTest : ContractVerifierBase() {

    private lateinit var anonymous: WebTestClient

    @BeforeEach
    fun anonymousClient() {
        anonymous = webTestClient.mutate().defaultHeaders { it.remove("Authorization") }.build()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/v1/merchants/$MERCHANT",
            "/v1/merchants/$MERCHANT/branches/$BRANCH",
            "/v1/merchants/$MERCHANT/products?branchId=$BRANCH",
            "/v1/merchants/$MERCHANT/products/$PRODUCT?branchId=$BRANCH",
            "/v1/merchants/$MERCHANT/stripe-connect/status",
        ]
    )
    fun `storefront catalog reads do not require a token`(uri: String) {
        val status = anonymous.get().uri(uri).exchange().returnResult(String::class.java).status
        assertNotEquals(HttpStatus.UNAUTHORIZED, status, "GET $uri")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/v1/merchants/$MERCHANT/branches",
            "/v1/merchants/$MERCHANT/employees",
            "/v1/merchants/$MERCHANT/provider-accounts",
            "/v1/merchants/$MERCHANT/stripe-connect/refresh",
            "/v1/merchants/nearby",
            "/v1/merchants/menu-languages",
            "/v1/merchants/$MERCHANT/products/$PRODUCT/translations",
            "/v1/merchants/$MERCHANT/translation-coverage",
            "/v1/merchant/orders",
        ]
    )
    fun `other reads still require a token`(uri: String) {
        anonymous.get().uri(uri).exchange().expectStatus().isUnauthorized
    }

    @org.junit.jupiter.api.Test
    fun `catalog writes still require a token`() {
        anonymous.put().uri("/v1/merchants/$MERCHANT").bodyValue("{}").exchange().expectStatus().isUnauthorized
        anonymous.delete().uri("/v1/merchants/$MERCHANT/products/$PRODUCT").exchange().expectStatus().isUnauthorized
        anonymous.put().uri("/v1/merchants/$MERCHANT/products/$PRODUCT/translations/en").bodyValue("{}")
            .exchange().expectStatus().isUnauthorized
    }

    companion object {
        const val MERCHANT = "00000000-0000-0000-0000-000000000001"
        const val BRANCH = "00000000-0000-0000-0000-000000000002"
        const val PRODUCT = "00000000-0000-0000-0000-000000000003"
    }
}
