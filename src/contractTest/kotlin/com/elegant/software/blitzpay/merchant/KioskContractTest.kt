package com.elegant.software.blitzpay.merchant

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantOperationsRole
import com.elegant.software.blitzpay.merchant.iam.domain.MerchantUserAssignment
import com.elegant.software.blitzpay.merchant.iam.web.KioskLoginResponse
import com.elegant.software.blitzpay.merchant.iam.web.KioskResponse
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKioskSettings
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.Base64
import java.util.Optional
import java.util.UUID

/** The Kiosks of a Branch (Keycloak provisioning mocked) and its Kiosk Settings (real service over mocked repositories). */
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
    private val kioskId = UUID.fromString("77777777-7777-7777-7777-777777777777")
    private val base = "/v1/merchant/branches/${branch.id}"

    private var stored: MerchantKioskSettings? = null

    private fun bearer(claims: String): String =
        "Bearer fakeheader.${Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray())}.fakesig"

    private val manager = bearer("""{"sub":"$managerId"}""")
    private val employee = bearer("""{"sub":"$employeeId"}""")
    private fun kiosk(branchId: UUID, azp: String = "blitzpay-kiosk") =
        bearer("""{"sub":"kiosk-user","azp":"$azp","groups":["branch_$branchId"]}""")

    private fun login(name: String = "Kiosk 1") = KioskLoginResponse(
        kioskId = kioskId, name = name, username = "kiosk-br-1a2b3c4d5e6f-1", password = "secret-password-shown-once",
        keycloakUrl = "https://auth.example", realm = "blitzpay", clientId = "blitzpay-kiosk",
        apiBaseUrl = "https://api.example", branchId = branch.id,
    )

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
        whenever(merchantKioskSettingsRepository.findByBranchId(branch.id)).thenAnswer { stored }
        whenever(merchantKioskSettingsRepository.save(any<MerchantKioskSettings>())).thenAnswer { stored = it.arguments[0] as MerchantKioskSettings; stored }
        whenever(merchantSupportedMenuLanguageRepository.findAllByMerchantId(merchantId)).thenReturn(
            listOf(com.elegant.software.blitzpay.merchant.persistence.model.MerchantSupportedMenuLanguage(merchantId, "de"))
        )
    }

    @Test
    fun `adding a kiosk provisions its login and returns the password once`() {
        whenever(kioskAccessService.create(any(), eq("Entrance"), anyOrNull())).thenReturn(login("Entrance"))

        webTestClient.post().uri("$base/kiosks").header(HttpHeaders.AUTHORIZATION, manager)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"name":"Entrance"}""")
            .exchange()
            .expectStatus().isCreated
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectBody()
            .jsonPath("$.kioskId").isEqualTo(kioskId.toString())
            .jsonPath("$.name").isEqualTo("Entrance")
            .jsonPath("$.username").isEqualTo("kiosk-br-1a2b3c4d5e6f-1")
            .jsonPath("$.password").isEqualTo("secret-password-shown-once")
            .jsonPath("$.clientId").isEqualTo("blitzpay-kiosk")
            .jsonPath("$.realm").isEqualTo("blitzpay")
            .jsonPath("$.branchId").isEqualTo(branch.id.toString())
    }

    @Test
    fun `a kiosk can be added without a body`() {
        whenever(kioskAccessService.create(any(), anyOrNull(), anyOrNull())).thenReturn(login())

        webTestClient.post().uri("$base/kiosks").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange().expectStatus().isCreated
            .expectBody().jsonPath("$.name").isEqualTo("Kiosk 1")
    }

    @Test
    fun `the list shows every kiosk of the branch without passwords`() {
        whenever(kioskAccessService.list(any())).thenReturn(
            listOf(
                KioskResponse(kioskId, 1, "Entrance", "kiosk-br-1a2b3c4d5e6f-1", enabled = true),
                KioskResponse(UUID.randomUUID(), 2, "Kiosk 2", "kiosk-br-1a2b3c4d5e6f-2", enabled = false),
            )
        )

        webTestClient.get().uri("$base/kiosks").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].enabled").isEqualTo(true)
            .jsonPath("$[1].username").isEqualTo("kiosk-br-1a2b3c4d5e6f-2")
            .jsonPath("$[0].password").doesNotExist()
    }

    @Test
    fun `enable resets the login, disable and delete revoke it`() {
        whenever(kioskAccessService.enable(any(), eq(kioskId), anyOrNull())).thenReturn(login())
        whenever(kioskAccessService.disable(any(), eq(kioskId))).thenReturn(KioskResponse(kioskId, 1, "Kiosk 1", "kiosk-br-1a2b3c4d5e6f-1", enabled = false))

        webTestClient.post().uri("$base/kiosks/$kioskId/enable").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.password").isEqualTo("secret-password-shown-once")
        webTestClient.post().uri("$base/kiosks/$kioskId/disable").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.enabled").isEqualTo(false).jsonPath("$.password").doesNotExist()
        webTestClient.delete().uri("$base/kiosks/$kioskId").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange().expectStatus().isNoContent
        verify(kioskAccessService).delete(any(), eq(kioskId))
    }

    @Test
    fun `only Merchant Managers and platform admins manage kiosks`() {
        listOf(employee, "Bearer $fakeJwt", kiosk(branch.id)).forEach { token ->
            webTestClient.get().uri("$base/kiosks").header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
            webTestClient.post().uri("$base/kiosks").header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
            webTestClient.post().uri("$base/kiosks/$kioskId/enable").header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
            webTestClient.post().uri("$base/kiosks/$kioskId/disable").header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
            webTestClient.delete().uri("$base/kiosks/$kioskId").header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
            webTestClient.put().uri("$base/kiosk/settings").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isForbidden
        }
        verify(kioskAccessService, never()).create(any(), anyOrNull(), anyOrNull())
        verify(merchantKioskSettingsRepository, never()).save(any<MerchantKioskSettings>())
    }

    @Test
    fun `a platform admin manages kiosks of any merchant`() {
        whenever(kioskAccessService.list(any())).thenReturn(emptyList())
        val admin = bearer("""{"sub":"admin","realm_access":{"roles":["blitzpay-admin"]}}""")

        webTestClient.get().uri("$base/kiosks").header(HttpHeaders.AUTHORIZATION, admin).exchange().expectStatus().isOk
    }

    @Test
    fun `unknown branch or kiosk is 404`() {
        val unknown = UUID.fromString("99999999-9999-9999-9999-999999999999")
        whenever(merchantBranchRepository.findById(unknown)).thenReturn(Optional.empty())
        whenever(kioskAccessService.enable(any(), eq(unknown), anyOrNull())).thenThrow(NoSuchElementException("Kiosk not found: $unknown"))

        webTestClient.post().uri("/v1/merchant/branches/$unknown/kiosks").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange().expectStatus().isNotFound
        webTestClient.post().uri("$base/kiosks/$unknown/enable").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange().expectStatus().isNotFound
    }

    @Test
    fun `settings saved by a manager are read by the branch's kiosks`() {
        webTestClient.put().uri("$base/kiosk/settings").header(HttpHeaders.AUTHORIZATION, manager)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"paymentChoices":["CASH","SCAN_TO_PAY"],"resetSeconds":45,"languages":["de"],"advertisedProductIds":[]}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentChoices[0]").isEqualTo("CASH")
            .jsonPath("$.resetSeconds").isEqualTo(45)
            .jsonPath("$.languages[0]").isEqualTo("de")

        listOf(kiosk(branch.id), manager).forEach { token ->
            webTestClient.get().uri("$base/kiosk/settings").header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.paymentChoices.length()").isEqualTo(2)
                .jsonPath("$.resetSeconds").isEqualTo(45)
        }
    }

    @Test
    fun `a branch without settings returns the defaults`() {
        webTestClient.get().uri("$base/kiosk/settings").header(HttpHeaders.AUTHORIZATION, manager)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentChoices.length()").isEqualTo(3)
            .jsonPath("$.resetSeconds").isEqualTo(30)
            .jsonPath("$.languages.length()").isEqualTo(0)
            .jsonPath("$.advertisedProductIds.length()").isEqualTo(0)
    }

    @Test
    fun `kiosks of other branches, other device logins and employees cannot read the settings`() {
        listOf(kiosk(otherBranchId), kiosk(branch.id, azp = "blitzpay-kitchen"), employee).forEach { token ->
            webTestClient.get().uri("$base/kiosk/settings").header(HttpHeaders.AUTHORIZATION, token).exchange().expectStatus().isForbidden
        }
        val anonymous = webTestClient.mutate().defaultHeaders { it.remove("Authorization") }.build()
        anonymous.get().uri("$base/kiosk/settings").exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `invalid settings are rejected with their error code`() {
        fun put(body: String, status: Int, code: String) =
            webTestClient.put().uri("$base/kiosk/settings").header(HttpHeaders.AUTHORIZATION, manager)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(body)
                .exchange().expectStatus().isEqualTo(status).expectBody().jsonPath("$.code").isEqualTo(code)

        put("""{"paymentChoices":["CARD","BITCOIN"]}""", 400, "UNKNOWN_KIOSK_PAYMENT_CHOICE")
        put("""{"paymentChoices":[]}""", 400, "KIOSK_PAYMENT_CHOICES_REQUIRED")
        put("""{"resetSeconds":4}""", 400, "KIOSK_RESET_SECONDS_OUT_OF_RANGE")
        put("""{"resetSeconds":301}""", 400, "KIOSK_RESET_SECONDS_OUT_OF_RANGE")
        put("""{"languages":["fr"]}""", 422, "KIOSK_LANGUAGE_NOT_SUPPORTED")
        put("""{"advertisedProductIds":["${UUID.randomUUID()}"]}""", 422, "KIOSK_ADVERTISED_PRODUCT_INVALID")
        verify(merchantKioskSettingsRepository, never()).save(any<MerchantKioskSettings>())
    }

    @Test
    fun `a manager sets the admin pin, which is never returned, and only the branch's kiosk can unlock with it`() {
        webTestClient.put().uri("$base/kiosk/settings").header(HttpHeaders.AUTHORIZATION, manager)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"adminPin":"4821"}""")
            .exchange().expectStatus().isOk
            .expectBody().jsonPath("$.adminPinSet").isEqualTo(true).jsonPath("$.adminPin").doesNotExist()

        fun unlock(token: String, pin: String) = webTestClient.post().uri("$base/kiosk/admin-unlock").header(HttpHeaders.AUTHORIZATION, token)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"pin":"$pin"}""").exchange()

        unlock(kiosk(branch.id), "4821").expectStatus().isNoContent
        unlock(kiosk(branch.id), "0000").expectStatus().isForbidden.expectBody().jsonPath("$.code").isEqualTo("KIOSK_ADMIN_PIN_WRONG")
        listOf(kiosk(otherBranchId), kiosk(branch.id, azp = "blitzpay-kitchen"), manager).forEach { unlock(it, "4821").expectStatus().isForbidden }
    }

    @Test
    fun `unlocking without an admin pin is 409 and a malformed pin is rejected`() {
        webTestClient.post().uri("$base/kiosk/admin-unlock").header(HttpHeaders.AUTHORIZATION, kiosk(branch.id))
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"pin":"1234"}""")
            .exchange().expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("KIOSK_ADMIN_PIN_NOT_SET")
        webTestClient.put().uri("$base/kiosk/settings").header(HttpHeaders.AUTHORIZATION, manager)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"adminPin":"12a"}""")
            .exchange().expectStatus().isBadRequest.expectBody().jsonPath("$.code").isEqualTo("KIOSK_ADMIN_PIN_INVALID")
    }
}
