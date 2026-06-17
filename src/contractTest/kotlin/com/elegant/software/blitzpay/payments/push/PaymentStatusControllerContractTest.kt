package com.elegant.software.blitzpay.payments.push

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import com.elegant.software.blitzpay.order.api.PaymentStatusCode
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusResponse
import com.elegant.software.blitzpay.payments.push.service.PaymentStatusService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.util.Optional
import java.util.UUID

class PaymentStatusControllerContractTest : ContractVerifierBase() {

    @MockitoBean
    private lateinit var paymentStatusService: PaymentStatusService

    @Test
    fun `returns 200 with settled status`() {
        val id = UUID.fromString("7f1a8e22-2d30-4e4e-9b83-0f1a5e8c7e01")
        whenever(paymentStatusService.getByRequestId(id)).thenReturn(
            Optional.of(
                PaymentStatusResponse(
                    paymentRequestId = id,
                    status = PaymentStatusCode.SETTLED,
                    terminal = true,
                    lastEventAt = Instant.parse("2026-04-15T09:12:31Z"),
                )
            )
        )

        webTestClient.get().uri("/v1/payments/$id")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.paymentRequestId").isEqualTo(id.toString())
            .jsonPath("$.status").isEqualTo("SETTLED")
            .jsonPath("$.terminal").isEqualTo(true)
            .jsonPath("$.lastEventAt").isEqualTo("2026-04-15T09:12:31Z")
    }

    @Test
    fun `returns 200 with pending status`() {
        val id = UUID.randomUUID()
        whenever(paymentStatusService.getByRequestId(id)).thenReturn(
            Optional.of(
                PaymentStatusResponse(
                    paymentRequestId = id,
                    status = PaymentStatusCode.PENDING,
                    terminal = false,
                    lastEventAt = null,
                )
            )
        )

        webTestClient.get().uri("/v1/payments/$id")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("PENDING")
            .jsonPath("$.terminal").isEqualTo(false)
            .jsonPath("$.lastEventAt").doesNotExist()
    }

    @Test
    fun `returns 404 when unknown`() {
        val id = UUID.randomUUID()
        whenever(paymentStatusService.getByRequestId(id)).thenReturn(Optional.empty())

        webTestClient.get().uri("/v1/payments/$id")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `returns 400 on malformed UUID`() {
        webTestClient.get().uri("/v1/payments/not-a-uuid")
            .exchange()
            .expectStatus().isBadRequest
    }
}