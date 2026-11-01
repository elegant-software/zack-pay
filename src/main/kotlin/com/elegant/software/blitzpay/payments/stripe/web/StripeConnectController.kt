package com.elegant.software.blitzpay.payments.stripe.web

import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.stripe.exception.StripeException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class StartOnboardingRequest(val returnUrl: String? = null, val refreshUrl: String? = null)
data class ConnectOnboardingResponse(val onboardingUrl: String)
enum class ConnectPaymentFlow { CARD, PAY_BY_BANK, TAP_TO_PAY }
data class ConnectStatusResponse(
    val connected: Boolean,
    val chargesEnabled: Boolean,
    val detailsSubmitted: Boolean,
    val enabledPaymentFlows: Set<ConnectPaymentFlow> = emptySet(),
)

@Tag(name = "Stripe Connect", description = "Stripe Connect account onboarding for merchants")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/stripe-connect", version = "1")
class StripeConnectController(
    private val stripeConnectService: StripeConnectService,
    private val stripeProperties: StripeProperties,
) {
    @Operation(summary = "Start Stripe Connect onboarding — creates a connected account and returns the hosted onboarding URL")
    @PostMapping
    fun startOnboarding(
        @PathVariable merchantId: UUID,
        @RequestBody(required = false) body: StartOnboardingRequest? = null,
    ): ResponseEntity<Any> {
        val returnUrl = body?.returnUrl?.takeIf { it.isNotBlank() } ?: stripeProperties.connect.returnUrl
        val refreshUrl = body?.refreshUrl?.takeIf { it.isNotBlank() } ?: refreshUrl(merchantId)
        return try {
            val url = stripeConnectService.createOnboardingUrl(merchantId, returnUrl, refreshUrl)
            ResponseEntity.ok(ConnectOnboardingResponse(url) as Any)
        } catch (ex: StripeException) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(ex.message ?: "Stripe error") as Any)
        }
    }

    @Operation(summary = "Get Stripe Connect onboarding status for this merchant")
    @GetMapping("/status")
    fun getStatus(@PathVariable merchantId: UUID): ResponseEntity<ConnectStatusResponse> {
        val accountId = stripeConnectService.findConnectedAccountId(merchantId)
        val chargesEnabled = accountId != null && stripeConnectService.isChargesEnabled(merchantId)
        val payByBankEnabled = accountId != null && stripeConnectService.isPayByBankEnabled(merchantId)
        val tapToPayEnabled = accountId != null && stripeConnectService.isTapToPayEnabled(merchantId)
        return ResponseEntity.ok(
            ConnectStatusResponse(
                connected = accountId != null,
                chargesEnabled = chargesEnabled,
                detailsSubmitted = accountId != null && stripeConnectService.isDetailsSubmitted(merchantId),
                enabledPaymentFlows = buildSet {
                    if (chargesEnabled) add(ConnectPaymentFlow.CARD)
                    if (payByBankEnabled) add(ConnectPaymentFlow.PAY_BY_BANK)
                    if (tapToPayEnabled) add(ConnectPaymentFlow.TAP_TO_PAY)
                },
            )
        )
    }

    @Operation(summary = "Refresh an expired Connect onboarding URL")
    @GetMapping("/refresh")
    fun refreshOnboardingUrl(
        @PathVariable merchantId: UUID,
        @RequestParam(required = false) returnUrl: String? = null,
        @RequestParam(required = false) refreshUrl: String? = null,
    ): ResponseEntity<Any> {
        val resolvedReturn = returnUrl?.takeIf { it.isNotBlank() } ?: stripeProperties.connect.returnUrl
        val resolvedRefresh = refreshUrl?.takeIf { it.isNotBlank() } ?: refreshUrl(merchantId)
        return try {
            val url = stripeConnectService.createRefreshUrl(merchantId, resolvedReturn, resolvedRefresh)
            ResponseEntity.ok(ConnectOnboardingResponse(url) as Any)
        } catch (ex: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(ex.message ?: "Merchant not Connect-onboarded") as Any)
        } catch (ex: StripeException) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse(ex.message ?: "Stripe error") as Any)
        }
    }

    @Operation(summary = "Enable Tap to Pay for this merchant — requires charges_enabled")
    @PostMapping("/tap-to-pay/enable")
    fun enableTapToPay(@PathVariable merchantId: UUID): ResponseEntity<Any> {
        if (!stripeConnectService.isChargesEnabled(merchantId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("Stripe Connect account is not verified for this merchant") as Any)
        }
        return try {
            stripeConnectService.enableTapToPay(merchantId)
            ResponseEntity.ok(mapOf("tapToPayEnabled" to true) as Any)
        } catch (ex: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(ex.message ?: "Merchant not Connect-onboarded") as Any)
        }
    }

    @Operation(summary = "Disable Tap to Pay for this merchant")
    @DeleteMapping("/tap-to-pay")
    fun disableTapToPay(@PathVariable merchantId: UUID): ResponseEntity<Any> {
        return try {
            stripeConnectService.disableTapToPay(merchantId)
            ResponseEntity.ok(mapOf("tapToPayEnabled" to false) as Any)
        } catch (ex: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(ex.message ?: "Merchant not Connect-onboarded") as Any)
        }
    }

    private fun refreshUrl(merchantId: UUID) =
        "${stripeProperties.connect.apiBaseUrl.trimEnd('/')}/v1/merchants/$merchantId/stripe-connect/refresh"
}
