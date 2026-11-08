package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders

class StorefrontLanguageContractTest : ContractVerifierBase() {
    @Test
    fun `anonymous guests receive only resource backed public language metadata`() {
        val client = webTestClient.mutate().defaultHeaders { it.remove(HttpHeaders.AUTHORIZATION) }.build()
        val expected = requireNotNull(javaClass.getResourceAsStream("/testdata/merchant/storefront-languages.json"))
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        client.get().uri("/v1/storefront/languages").exchange().expectStatus().isOk.expectBody().json(expected)
            .jsonPath("$[0].adminUiEnabled").doesNotExist()
            .jsonPath("$[0].menuEnabled").doesNotExist()
        client.post().uri("/v1/storefront/languages").exchange().expectStatus().isUnauthorized
        client.put().uri("/v1/storefront/languages").exchange().expectStatus().isUnauthorized
        client.patch().uri("/v1/storefront/languages").exchange().expectStatus().isUnauthorized
        client.delete().uri("/v1/storefront/languages").exchange().expectStatus().isUnauthorized
        client.get().uri("/v1/merchants/menu-languages").exchange().expectStatus().isUnauthorized
    }
}
