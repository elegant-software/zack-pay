package com.elegant.software.blitzpay.payments.adyen.web

import com.adyen.service.exception.ApiException
import com.elegant.software.blitzpay.payments.adyen.service.AdyenConnectService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
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

// NOTE: This controller is admin-facing. All endpoints require admin authentication.
// Actual security enforcement (e.g. Keycloak role check) is out of scope for this module
// and must be applied at the API gateway or security filter level.

data class AdyenOnboardingRequest(
    val returnUrl: String = "",
    val redirectUrl: String = "",
)

data class AdyenOnboardingResponse(val onboardingUrl: String)

data class AdyenConnectStatusResponse(
    val legalEntityId: String?,
    val balanceAccountId: String?,
    val receivePaymentsEnabled: Boolean,
    val detailsSubmitted: Boolean,
)

data class AdyenErrorResponse(val error: String)

@Tag(name = "Adyen Connect", description = "Adyen for Platforms — Balance Platform onboarding for merchants (admin-facing)")
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/adyen-connect", version = "1")
class AdyenConnectController(
    private val adyenConnectService: AdyenConnectService,
) {
    private val log = LoggerFactory.getLogger(AdyenConnectController::class.java)

    @Operation(summary = "Start Adyen onboarding — creates LegalEntity + AccountHolder + BalanceAccount and returns the hosted onboarding URL")
    @PostMapping("/onboarding")
    fun startOnboarding(
        @PathVariable merchantId: UUID,
        @RequestBody(required = false) body: AdyenOnboardingRequest? = null,
    ): Mono<ResponseEntity<Any>> {
        val returnUrl = body?.returnUrl?.takeIf { it.isNotBlank() } ?: ""
        val redirectUrl = body?.redirectUrl?.takeIf { it.isNotBlank() } ?: ""
        return adyenConnectService.createOnboardingUrl(merchantId, returnUrl, redirectUrl)
            .map { url -> ResponseEntity.ok(AdyenOnboardingResponse(url) as Any) }
            .onErrorResume(ApiException::class.java) { ex ->
                log.warn("adyen onboarding failed merchantId={} status={} error={}", merchantId, ex.statusCode, ex.message)
                Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(AdyenErrorResponse(ex.message ?: "Adyen API error") as Any)
                )
            }
            .onErrorResume(NoSuchElementException::class.java) { ex ->
                Mono.just(
                    ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(AdyenErrorResponse(ex.message ?: "Not found") as Any)
                )
            }
    }

    @Operation(summary = "Refresh an expired Adyen onboarding URL")
    @GetMapping("/refresh")
    fun refreshOnboardingUrl(
        @PathVariable merchantId: UUID,
        @RequestParam(required = false) returnUrl: String? = null,
    ): Mono<ResponseEntity<Any>> {
        val resolvedReturnUrl = returnUrl?.takeIf { it.isNotBlank() } ?: ""
        return adyenConnectService.createRefreshUrl(merchantId, resolvedReturnUrl)
            .map { url -> ResponseEntity.ok(AdyenOnboardingResponse(url) as Any) }
            .onErrorResume(NoSuchElementException::class.java) { ex ->
                Mono.just(
                    ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(AdyenErrorResponse(ex.message ?: "Merchant not Adyen-onboarded") as Any)
                )
            }
            .onErrorResume(ApiException::class.java) { ex ->
                log.warn("adyen refresh failed merchantId={} status={} error={}", merchantId, ex.statusCode, ex.message)
                Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(AdyenErrorResponse(ex.message ?: "Adyen API error") as Any)
                )
            }
    }

    @Operation(summary = "Get Adyen onboarding status for this merchant")
    @GetMapping("/status")
    fun getStatus(@PathVariable merchantId: UUID): ResponseEntity<AdyenConnectStatusResponse> {
        val legalEntityId = adyenConnectService.findLegalEntityId(merchantId)
        val balanceAccountId = adyenConnectService.findBalanceAccountId(merchantId)
        val receivePaymentsEnabled = legalEntityId != null && adyenConnectService.isReceivePaymentsEnabled(merchantId)
        return ResponseEntity.ok(
            AdyenConnectStatusResponse(
                legalEntityId = legalEntityId,
                balanceAccountId = balanceAccountId,
                receivePaymentsEnabled = receivePaymentsEnabled,
                detailsSubmitted = adyenConnectService.findLegalEntityId(merchantId) != null,
            )
        )
    }
}
