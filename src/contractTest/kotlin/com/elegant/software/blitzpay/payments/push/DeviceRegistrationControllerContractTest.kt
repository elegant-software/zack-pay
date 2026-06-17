package com.elegant.software.blitzpay.payments.push

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.payments.push.web.DeviceRegistrationRequest
import com.elegant.software.blitzpay.payments.push.web.DeviceRegistrationResponse
import com.elegant.software.blitzpay.payments.push.service.DeviceRegistrationService
import com.elegant.software.blitzpay.payments.push.service.OrderNotFoundException
import com.elegant.software.blitzpay.payments.push.service.RegistrationOutcome
import com.elegant.software.blitzpay.payments.push.persistence.model.DevicePlatform
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

class DeviceRegistrationControllerContractTest : ContractVerifierBase() {

    @MockitoBean
    private lateinit var deviceRegistrationService: DeviceRegistrationService

    @Test
    fun `returns 201 when token is new`() {
        val orderId = "ORD-ABC123456789"
        val token = "ExponentPushToken[abc123]"
        val id = UUID.randomUUID()
        whenever(deviceRegistrationService.register(any())).thenReturn(
            RegistrationOutcome(
                response = DeviceRegistrationResponse(
                    id = id,
                    orderId = orderId,
                    expoPushToken = token,
                    platform = DevicePlatform.IOS,
                ),
                created = true,
            )
        )

        webTestClient.post().uri("/v1/devices")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"orderId":"$orderId","expoPushToken":"$token","platform":"IOS"}
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.id").isEqualTo(id.toString())
            .jsonPath("$.expoPushToken").isEqualTo(token)
    }

    @Test
    fun `returns 200 when token already exists`() {
        val orderId = "ORD-ABC123456789"
        val token = "ExponentPushToken[existing]"
        val id = UUID.randomUUID()
        whenever(deviceRegistrationService.register(any())).thenReturn(
            RegistrationOutcome(
                response = DeviceRegistrationResponse(
                    id = id,
                    orderId = orderId,
                    expoPushToken = token,
                    platform = DevicePlatform.ANDROID,
                ),
                created = false,
            )
        )

        webTestClient.post().uri("/v1/devices")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"orderId":"$orderId","expoPushToken":"$token","platform":"ANDROID"}
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.id").isEqualTo(id.toString())
    }

    @Test
    fun `returns 400 on malformed token`() {
        val orderId = "ORD-ABC123456789"
        webTestClient.post().uri("/v1/devices")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"orderId":"$orderId","expoPushToken":"not-a-valid-token"}
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `returns 404 when order unknown`() {
        val orderId = "ORD-MISSING12345"
        val token = "ExponentPushToken[xyz]"
        doThrow(OrderNotFoundException(orderId))
            .whenever(deviceRegistrationService).register(any<DeviceRegistrationRequest>())

        webTestClient.post().uri("/v1/devices")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                """
                {"orderId":"$orderId","expoPushToken":"$token"}
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `returns 204 on unregister`() {
        val token = "ExponentPushToken[bye]"
        webTestClient.delete().uri("/v1/devices/$token")
            .exchange()
            .expectStatus().isNoContent
    }
}
