package com.elegant.software.blitzpay.contract

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import java.util.Base64

class ActuatorAccessContractTest : ContractVerifierBase() {

    private fun bearer(realmRoles: List<String>): String {
        val roles = realmRoles.joinToString(",") { "\"$it\"" }
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"u","realm_access":{"roles":[$roles]}}""".toByteArray())
        return "Bearer fakeheader.$payload.fakesig"
    }

    @Test
    fun `health stays public`() {
        webTestClient.get().uri("/actuator/health").exchange().expectStatus().isOk
    }

    @Test
    fun `non-admin is forbidden from other actuator endpoints`() {
        webTestClient.get().uri("/actuator/loggers")
            .header(HttpHeaders.AUTHORIZATION, bearer(listOf("offline_access")))
            .exchange().expectStatus().isForbidden
    }

    @Test
    fun `platform admin passes the actuator rule`() {
        // loggers isn't exposed under contract-test, so 404 proves security let the admin through.
        webTestClient.get().uri("/actuator/loggers")
            .header(HttpHeaders.AUTHORIZATION, bearer(listOf("blitzpay-admin")))
            .exchange().expectStatus().isNotFound
    }
}
