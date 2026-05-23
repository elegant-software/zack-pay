package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.contract.ContractVerifierBase
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.UUID

class StripeConnectControllerContractTest : ContractVerifierBase() {

    private val testMerchantId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `returns stripe connect status with pay by bank flow`() {
        whenever(stripeConnectGateway.findConnectedAccountId(any())).thenReturn("acct_test_contract")
        whenever(stripeConnectGateway.isChargesEnabled(any())).thenReturn(true)
        whenever(stripeConnectGateway.isDetailsSubmitted(any())).thenReturn(true)
        whenever(stripeConnectGateway.isPayByBankEnabled(any())).thenReturn(true)

        webTestClient.get()
            .uri("/v1/merchants/$testMerchantId/stripe-connect/status")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.connected").isEqualTo(true)
            .jsonPath("$.chargesEnabled").isEqualTo(true)
            .jsonPath("$.detailsSubmitted").isEqualTo(true)
            .jsonPath("$.enabledPaymentFlows[0]").isEqualTo("CARD")
            .jsonPath("$.enabledPaymentFlows[1]").isEqualTo("PAY_BY_BANK")
    }
}
