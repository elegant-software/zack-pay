package com.elegant.software.blitzpay.usersettings

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.usersettings.persistence.model.UserSetting
import com.elegant.software.blitzpay.usersettings.persistence.model.UserSettingId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.http.MediaType
import java.time.Duration

class UserSettingsContractTest : ContractVerifierBase() {
    @org.springframework.test.context.bean.override.mockito.MockitoBean(name = "mockJwtDecoder")
    private lateinit var decoder: org.springframework.security.oauth2.jwt.JwtDecoder

    @org.junit.jupiter.api.BeforeEach
    fun configureDecoder() {
        whenever(decoder.decode(any())).thenAnswer { invocation ->
            val token = invocation.getArgument<String>(0)
            org.springframework.security.oauth2.jwt.Jwt.withTokenValue(token).header("alg", "none")
                .subject(if (token == "other-account-token") "other-account" else "contract-test-user").build()
        }
    }

    @Test
    fun `OpenAPI app language values match the JSON domain enum`() {
        // Schema generation is substantially slower than an ordinary API response on a cold context.
        webTestClient.mutate().responseTimeout(Duration.ofSeconds(60)).build()
            .get().uri("/api-docs").exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.components.schemas.AppLanguage.enum").isEqualTo(listOf("en", "de"))
    }

    @Test
    fun `different authenticated accounts read only their own settings`() {
        whenever(userSettingRepository.findAllByIdUserId("contract-test-user")).thenReturn(listOf(
            UserSetting(UserSettingId("contract-test-user", "merchantAppLanguage"), "de"),
        ))
        webTestClient.get().uri("/v1/user-settings").exchange().expectStatus().isOk
            .expectBody().jsonPath("$.merchantAppLanguage").isEqualTo("de")
        webTestClient.get().uri("/v1/user-settings").headers { it.setBearerAuth("other-account-token") }
            .exchange().expectStatus().isOk.expectBody().jsonPath("$.merchantAppLanguage").isEmpty
        verify(userSettingRepository).findAllByIdUserId("other-account")
    }

    @Test
    fun `GET leaves unsaved preferences unset for client default resolution`() {
        webTestClient.get().uri("/v1/user-settings").exchange()
            .expectStatus().isOk.expectBody()
            .jsonPath("$.shopperAppLanguage").isEmpty
            .jsonPath("$.merchantAppLanguage").isEmpty
        verify(userSettingRepository).findAllByIdUserId("contract-test-user")
    }

    @Test
    fun `explicit English remains distinct from an unset preference`() {
        whenever(userSettingRepository.findAllByIdUserId("contract-test-user")).thenReturn(listOf(
            UserSetting(UserSettingId("contract-test-user", "shopperAppLanguage"), "en"),
        ))
        webTestClient.get().uri("/v1/user-settings").exchange().expectStatus().isOk.expectBody()
            .jsonPath("$.shopperAppLanguage").isEqualTo("en")
            .jsonPath("$.merchantAppLanguage").isEmpty
    }

    @Test
    fun `PATCH changes only provided preference using verified subject`() {
        whenever(userSettingRepository.findAllByIdUserId("contract-test-user")).thenReturn(listOf(
            UserSetting(UserSettingId("contract-test-user", "shopperAppLanguage"), "de"),
        ))
        webTestClient.patch().uri("/v1/user-settings").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"shopperAppLanguage":"de","userId":"victim"}""").exchange()
            .expectStatus().isOk.expectBody()
            .jsonPath("$.shopperAppLanguage").isEqualTo("de")
            .jsonPath("$.merchantAppLanguage").isEmpty
        verify(userSettingRepository).upsert("contract-test-user", "shopperAppLanguage", "de")
        verify(userSettingRepository, never()).upsert(eq("victim"), any(), any())
        verify(userSettingRepository, never()).upsert(any(), eq("merchantAppLanguage"), any())
    }

    @Test
    fun `PATCH merchant language is independent and null fields preserve preferences`() {
        webTestClient.patch().uri("/v1/user-settings").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"shopperAppLanguage":null,"merchantAppLanguage":"de"}""").exchange().expectStatus().isOk
        verify(userSettingRepository).upsert("contract-test-user", "merchantAppLanguage", "de")
        verify(userSettingRepository, never()).upsert(any(), eq("shopperAppLanguage"), any())
    }

    @Test
    fun `PATCH another authenticated account updates only its own preference`() {
        webTestClient.patch().uri("/v1/user-settings").headers { it.setBearerAuth("other-account-token") }
            .contentType(MediaType.APPLICATION_JSON).bodyValue("""{"merchantAppLanguage":"de"}""")
            .exchange().expectStatus().isOk
        verify(userSettingRepository).upsert("other-account", "merchantAppLanguage", "de")
        verify(userSettingRepository, never()).upsert(eq("contract-test-user"), any(), any())
    }

    @Test
    fun `PATCH rejects unsupported languages`() {
        for (value in listOf("\"fr\"", "\"EN\"", "0")) {
            webTestClient.patch().uri("/v1/user-settings").contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"merchantAppLanguage\":$value}").exchange().expectStatus().isBadRequest
        }
        verify(userSettingRepository, never()).upsert(any(), any(), any())
    }

    @Test
    fun `GET and PATCH require authentication`() {
        val anonymous = webTestClient.mutate().defaultHeaders { it.remove("Authorization") }.build()
        anonymous.get().uri("/v1/user-settings").exchange().expectStatus().isUnauthorized
        anonymous.patch().uri("/v1/user-settings").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"shopperAppLanguage":"de"}""").exchange().expectStatus().isUnauthorized
        verifyNoInteractions(userSettingRepository)
    }
}
