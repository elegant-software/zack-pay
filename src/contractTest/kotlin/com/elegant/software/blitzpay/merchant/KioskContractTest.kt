package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment
import com.elegant.software.blitzpay.merchant.iam.service.KioskTokenService
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKiosk
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKioskToken
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.time.Instant
import java.util.Base64
import java.util.Optional
import java.util.UUID

/** Kiosk Login lifecycle, Kiosk Settings and Kiosk token authentication. Runs the real service over mocked repositories. */
class KioskContractTest : ContractVerifierBase() {

    private val merchantId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val managerId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val employeeId = UUID.fromString("66666666-6666-6666-6666-666666666666")
    private val branch = MerchantBranch(
        id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
        merchantId = merchantId,
        name = "Altstadt",
        branchCode = "BR-1A2B3C4D5E6F",
    )
    private val otherBranchId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val base = "/v1/merchant/branches/${branch.id}/kiosk"

    private var stored: MerchantKiosk? = null

    private fun bearer(sub: UUID): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$sub"}""".toByteArray())
        return "Bearer fakeheader.$payload.fakesig"
    }

    private val manager = bearer(managerId)
    private val employee = bearer(employeeId)

    @BeforeEach
    fun givenBranchAndRepositories() {
        stored = null
        whenever(merchantBranchRepository.findById(branch.id)).thenReturn(Optional.of(branch))
        whenever(merchantUserAssignmentRepository.findAllByKeycloakUserId(managerId)).thenReturn(
            listOf(MerchantUserAssignment(keycloakUserId = managerId, merchantId = merchantId, role = MerchantOperationsRole.MERCHANT_MANAGER))
        )
        whenever(merchantUserAssignmentRepository.findAllByKeycloakUserId(employeeId)).thenReturn(
            listOf(MerchantUserAssignment(keycloakUserId = employeeId, merchantId = merchantId, branchId = branch.id, role = MerchantOperationsRole.MERCHANT_EMPLOYEE))
        )
        whenever(merchantKioskRepository.findByBranchId(branch.id)).thenAnswer { stored }
        whenever(merchantKioskRepository.findByLogin(any())).thenAnswer { inv -> stored?.takeIf { it.login == inv.arguments[0] } }
        whenever(merchantKioskRepository.save(any<MerchantKiosk>())).thenAnswer { stored = it.arguments[0] as MerchantKiosk; stored }
        whenever(merchantKioskTokenRepository.save(any<MerchantKioskToken>())).thenAnswer { it.arguments[0] }
        whenever(merchantSupportedMenuLanguageRepository.findAllByMerchantId(merchantId)).thenReturn(
            listOf(com.elegant.software.blitzpay.merchant.persistence.model.MerchantSupportedMenuLanguage(merchantId, "de"))
        )
    }

    private fun enable(): Pair<String, String> {
        var login = ""
        var password = ""
        webTestClient.post().uri("$base/enable").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectBody()
            .jsonPath("$.login").value<String> { login = it }
            .jsonPath("$.password").value<String> { password = it }
        return login to password
    }

    private fun signIn(login: String, password: String): String {
        var token = ""
        webTestClient.post().uri("/v1/kiosk/login").header(HttpHeaders.AUTHORIZATION, "")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"login":"$login","password":"$password"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.branchId").isEqualTo(branch.id.toString())
            .jsonPath("$.merchantId").isEqualTo(merchantId.toString())
            .jsonPath("$.token").value<String> { token = it }
        return token
    }

    /** Makes [token] resolve like the database would after sign-in. */
    private fun knownToken(token: String, branchId: UUID = branch.id) {
        whenever(merchantKioskTokenRepository.findByTokenHash(KioskTokenService.hash(token))).thenReturn(
            MerchantKioskToken(merchantId = merchantId, branchId = branchId, tokenHash = KioskTokenService.hash(token), expiresAt = Instant.now().plusSeconds(60))
        )
    }

    @Test
    fun `enable returns login and password once, status never shows the password`() {
        val (login, password) = enable()

        org.junit.jupiter.api.Assertions.assertEquals("kiosk-br-1a2b3c4d5e6f", login)
        org.junit.jupiter.api.Assertions.assertTrue(password.length >= 32)
        webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(true)
            .jsonPath("$.login").isEqualTo(login)
            .jsonPath("$.password").doesNotExist()
            .jsonPath("$.settings.paymentChoices[0]").isEqualTo("CARD")
            .jsonPath("$.settings.paymentChoices.length()").isEqualTo(3)
            .jsonPath("$.settings.resetSeconds").isEqualTo(30)
            .jsonPath("$.settings.languages.length()").isEqualTo(0)
            .jsonPath("$.settings.advertisedProductIds.length()").isEqualTo(0)
    }

    @Test
    fun `status of a branch without a kiosk is disabled with null login and default settings`() {
        webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(false)
            .jsonPath("$.login").isEmpty
            .jsonPath("$.settings.resetSeconds").isEqualTo(30)
    }

    @Test
    fun `reset returns a different password`() {
        val first = enable()
        val second = enable()

        org.junit.jupiter.api.Assertions.assertNotEquals(first.second, second.second)
        webTestClient.post().uri("/v1/kiosk/login").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"login":"${first.first}","password":"${first.second}"}""")
            .exchange().expectStatus().isUnauthorized
        signIn(second.first, second.second)
    }

    @Test
    fun `disable revokes the login and sessions`() {
        val (login, password) = enable()

        webTestClient.post().uri("$base/disable").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.enabled").isEqualTo(false)
            .jsonPath("$.login").isEqualTo(login)
            .jsonPath("$.password").doesNotExist()
        verify(merchantKioskTokenRepository).deleteAllByBranchId(branch.id)
        webTestClient.post().uri("/v1/kiosk/login").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"login":"$login","password":"$password"}""")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody().jsonPath("$.code").isEqualTo("INVALID_KIOSK_CREDENTIALS")
    }

    @Test
    fun `login with a wrong password is 401 and needs no token`() {
        val (login, _) = enable()

        webTestClient.post().uri("/v1/kiosk/login").header(HttpHeaders.AUTHORIZATION, "")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"login":"$login","password":"wrong"}""")
            .exchange()
            .expectStatus().isUnauthorized
            .expectBody().jsonPath("$.code").isEqualTo("INVALID_KIOSK_CREDENTIALS")
    }

    @Test
    fun `only Merchant Managers and platform admins manage the kiosk`() {
        listOf(employee, "Bearer $fakeJwt").forEach { token ->
            webTestClient.post().uri("$base/enable").header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
            webTestClient.post().uri("$base/disable").header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
            webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
            webTestClient.put().uri("$base/settings").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isForbidden
        }
        verify(merchantKioskRepository, never()).save(any<MerchantKiosk>())
    }

    @Test
    fun `unknown branch is 404`() {
        val unknown = UUID.fromString("99999999-9999-9999-9999-999999999999")
        whenever(merchantBranchRepository.findById(unknown)).thenReturn(Optional.empty())

        webTestClient.post().uri("/v1/merchant/branches/$unknown/kiosk/enable").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange().expectStatus().isNotFound
    }

    @Test
    fun `settings can be saved and the kiosk reads them with its token`() {
        val token = "kt_test-token".also(::knownToken)
        stored = MerchantKiosk(branchId = branch.id, enabled = true, login = "kiosk-br-1a2b3c4d5e6f")

        webTestClient.put().uri("$base/settings").header(HttpHeaders.AUTHORIZATION, manager)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"paymentChoices":["CASH","SCAN_TO_PAY"],"resetSeconds":45,"languages":["de"],"advertisedProductIds":[]}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentChoices[0]").isEqualTo("CASH")
            .jsonPath("$.resetSeconds").isEqualTo(45)
            .jsonPath("$.languages[0]").isEqualTo("de")

        webTestClient.get().uri("/v1/kiosk/settings").header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentChoices.length()").isEqualTo(2)
            .jsonPath("$.resetSeconds").isEqualTo(45)
            .jsonPath("$.languages[0]").isEqualTo("de")
    }

    @Test
    fun `invalid settings are rejected with their error code`() {
        fun put(body: String, status: Int, code: String) =
            webTestClient.put().uri("$base/settings").header(HttpHeaders.AUTHORIZATION, manager)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isEqualTo(status).expectBody().jsonPath("$.code").isEqualTo(code)

        put("""{"paymentChoices":["CARD","BITCOIN"]}""", 400, "UNKNOWN_KIOSK_PAYMENT_CHOICE")
        put("""{"paymentChoices":[]}""", 400, "KIOSK_PAYMENT_CHOICES_REQUIRED")
        put("""{"resetSeconds":4}""", 400, "KIOSK_RESET_SECONDS_OUT_OF_RANGE")
        put("""{"resetSeconds":301}""", 400, "KIOSK_RESET_SECONDS_OUT_OF_RANGE")
        put("""{"languages":["fr"]}""", 422, "KIOSK_LANGUAGE_NOT_SUPPORTED")
        put("""{"advertisedProductIds":["${UUID.randomUUID()}"]}""", 422, "KIOSK_ADVERTISED_PRODUCT_INVALID")
        verify(merchantKioskRepository, never()).save(any<MerchantKiosk>())
    }

    @Test
    fun `kiosk settings need a valid kiosk token`() {
        val anonymous = webTestClient.mutate().defaultHeaders { it.remove("Authorization") }.build()
        anonymous.get().uri("/v1/kiosk/settings").exchange().expectStatus().isUnauthorized

        // A Merchant/shopper JWT is not a Kiosk
        webTestClient.get().uri("/v1/kiosk/settings").header(HttpHeaders.AUTHORIZATION, manager).exchange().expectStatus().isForbidden

        webTestClient.get().uri("/v1/kiosk/settings").header(HttpHeaders.AUTHORIZATION, "Bearer kt_unknown").exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `a disabled kiosk's token is rejected`() {
        val token = "kt_revoked".also(::knownToken)
        stored = MerchantKiosk(branchId = branch.id, enabled = false)

        webTestClient.get().uri("/v1/kiosk/settings").header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `a kiosk token only reads its own branch settings and cannot reach merchant endpoints`() {
        val token = "kt_branch-a".also(::knownToken)
        stored = MerchantKiosk(branchId = branch.id, enabled = true, resetSeconds = 77)
        whenever(merchantKioskRepository.findByBranchId(otherBranchId)).thenReturn(MerchantKiosk(branchId = otherBranchId, enabled = true, resetSeconds = 12))

        webTestClient.get().uri("/v1/kiosk/settings").header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.resetSeconds").isEqualTo(77)

        // Kiosk tokens are rejected on authenticated merchant endpoints, whoever the caller claims to be
        webTestClient.get().uri(base).header(HttpHeaders.AUTHORIZATION, "Bearer $token").exchange().expectStatus().isForbidden
        webTestClient.post().uri("$base/enable").header(HttpHeaders.AUTHORIZATION, "Bearer $token").exchange().expectStatus().isForbidden
    }
}
