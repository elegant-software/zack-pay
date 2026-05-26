package com.elegant.software.blitzpay.payments.stripe.internal

import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.stripe.exception.StripeException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import java.util.UUID

data class StartOnboardingRequest(val returnUrl: String? = null, val refreshUrl: String? = null)
data class ConnectOnboardingResponse(val onboardingUrl: String)
enum class ConnectPaymentFlow { CARD, PAY_BY_BANK }
data class ConnectStatusResponse(
    val connected: Boolean,
    val chargesEnabled: Boolean,
    val detailsSubmitted: Boolean,
    val enabledPaymentFlows: Set<ConnectPaymentFlow> = emptySet(),
)

@Tag(name = "Stripe Connect", description = "Stripe Connect Express account onboarding for merchants")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/stripe-connect", version = "1")
class StripeConnectController(
    private val stripeConnectService: StripeConnectService,
    private val stripeProperties: StripeProperties,
) {
    @Operation(summary = "Start Stripe Connect onboarding — creates an Express account and returns the hosted onboarding URL")
    @PostMapping
    fun startOnboarding(
        @PathVariable merchantId: UUID,
        @RequestBody(required = false) body: StartOnboardingRequest? = null,
    ): Mono<ResponseEntity<Any>> {
        val returnUrl = body?.returnUrl?.takeIf { it.isNotBlank() } ?: stripeProperties.connect.returnUrl
        val refreshUrl = body?.refreshUrl?.takeIf { it.isNotBlank() } ?: refreshUrl(merchantId)
        return stripeConnectService.createOnboardingUrl(merchantId, returnUrl, refreshUrl)
            .map { url -> ResponseEntity.ok(ConnectOnboardingResponse(url) as Any) }
            .onErrorResume(StripeException::class.java) { ex ->
                Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse(ex.message ?: "Stripe error") as Any))
            }
    }

    @Operation(summary = "Get Stripe Connect onboarding status for this merchant")
    @GetMapping("/status")
    fun getStatus(@PathVariable merchantId: UUID): ResponseEntity<ConnectStatusResponse> {
        val accountId = stripeConnectService.findConnectedAccountId(merchantId)
        val chargesEnabled = accountId != null && stripeConnectService.isChargesEnabled(merchantId)
        val payByBankEnabled = accountId != null && stripeConnectService.isPayByBankEnabled(merchantId)
        val enabledPaymentFlows = buildSet {
            if (chargesEnabled) add(ConnectPaymentFlow.CARD)
            if (payByBankEnabled) add(ConnectPaymentFlow.PAY_BY_BANK)
        }
        return ResponseEntity.ok(
            ConnectStatusResponse(
                connected = accountId != null,
                chargesEnabled = chargesEnabled,
                detailsSubmitted = accountId != null && stripeConnectService.isDetailsSubmitted(merchantId),
                enabledPaymentFlows = enabledPaymentFlows,
            )
        )
    }

    @Operation(summary = "Refresh an expired Connect onboarding URL — Stripe redirects here when the onboarding link expires")
    @GetMapping("/refresh")
    fun refreshOnboardingUrl(
        @PathVariable merchantId: UUID,
        @RequestParam(required = false) returnUrl: String? = null,
        @RequestParam(required = false) refreshUrl: String? = null,
    ): Mono<ResponseEntity<Any>> {
        val resolvedReturn = returnUrl?.takeIf { it.isNotBlank() } ?: stripeProperties.connect.returnUrl
        val resolvedRefresh = refreshUrl?.takeIf { it.isNotBlank() } ?: refreshUrl(merchantId)
        return stripeConnectService.createRefreshUrl(merchantId, resolvedReturn, resolvedRefresh)
            .map { url -> ResponseEntity.ok(ConnectOnboardingResponse(url) as Any) }
            .onErrorResume(NoSuchElementException::class.java) { ex ->
                Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorResponse(ex.message ?: "Merchant not Connect-onboarded") as Any))
            }
            .onErrorResume(StripeException::class.java) { ex ->
                Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse(ex.message ?: "Stripe error") as Any))
            }
    }

    private fun refreshUrl(merchantId: UUID) =
        "${stripeProperties.connect.apiBaseUrl.trimEnd('/')}/v1/merchants/$merchantId/stripe-connect/refresh"
}
