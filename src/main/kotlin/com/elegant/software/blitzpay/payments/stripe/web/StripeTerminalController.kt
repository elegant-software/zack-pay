package com.elegant.software.blitzpay.payments.stripe.internal

import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.stripe.exception.StripeException
import com.stripe.model.terminal.ConnectionToken
import com.stripe.model.terminal.Location
import com.stripe.net.RequestOptions
import com.stripe.param.terminal.ConnectionTokenCreateParams
import com.stripe.param.terminal.LocationCreateParams
import io.swagger.v3.oas.annotations.Operation
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

data class ConnectionTokenRequest(val merchantId: UUID, val branchId: UUID)
data class ConnectionTokenResponse(val secret: String, val locationId: String)

@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/payments/stripe/terminal", version = "1")
class StripeTerminalController(
    private val stripeConnectGateway: StripeConnectGateway,
    private val stripeProperties: StripeProperties,
) {
    private val log = LoggerFactory.getLogger(StripeTerminalController::class.java)

    @Operation(summary = "Issue a Stripe Terminal ConnectionToken for the merchant app SDK")
    @PostMapping("/connection-token")
    fun connectionToken(@RequestBody request: ConnectionTokenRequest): Mono<ResponseEntity<Any>> =
        Mono.fromCallable {
            val merchantId = request.merchantId
            val branchId = request.branchId

            val connectedAccountId = stripeConnectGateway.findConnectedAccountId(merchantId)
                ?.takeIf { stripeConnectGateway.isChargesEnabled(merchantId) }
                ?: return@fromCallable ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ErrorResponse("Stripe Connect account not configured or not yet verified for this merchant") as Any)

            if (!stripeConnectGateway.isTapToPayEnabled(merchantId)) {
                return@fromCallable ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ErrorResponse("Tap to Pay is not enabled for this merchant") as Any)
            }

            val locationId = resolveOrCreateLocation(merchantId, branchId, connectedAccountId)
                ?: return@fromCallable ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(ErrorResponse("Branch not found or missing address — cannot create Stripe Terminal Location") as Any)

            val requestOptions = RequestOptions.builder()
                .setApiKey(stripeProperties.secretKey)
                .setStripeAccount(connectedAccountId)
                .build()

            val token = ConnectionToken.create(
                ConnectionTokenCreateParams.builder().setLocation(locationId).build(),
                requestOptions,
            )
            log.info(
                "stripe terminal connection_token issued merchantId={} branchId={} locationId={}",
                merchantId, branchId, locationId,
            )
            ResponseEntity.ok(ConnectionTokenResponse(secret = token.secret, locationId = locationId) as Any)
        }
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(StripeException::class.java) { ex ->
                log.error("stripe terminal connection_token failed code={} message={}", ex.code, ex.message)
                Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ErrorResponse(ex.message ?: "Stripe error") as Any)
                )
            }

    private fun resolveOrCreateLocation(merchantId: UUID, branchId: UUID, connectedAccountId: String): String? {
        stripeConnectGateway.findTerminalLocationId(branchId)?.let { return it }

        val branchInfo = stripeConnectGateway.getBranchTerminalInfo(branchId) ?: return null

        val requestOptions = RequestOptions.builder()
            .setApiKey(stripeProperties.secretKey)
            .setStripeAccount(connectedAccountId)
            .build()

        val addressBuilder = LocationCreateParams.Address.builder()
            .apply { branchInfo.addressLine1?.let { setLine1(it) } }
            .apply { branchInfo.city?.let { setCity(it) } }
            .apply { branchInfo.postalCode?.let { setPostalCode(it) } }
            .setCountry(branchInfo.country ?: "GB")

        val location = Location.create(
            LocationCreateParams.builder()
                .setDisplayName(branchInfo.branchName)
                .setAddress(addressBuilder.build())
                .build(),
            requestOptions,
        )
        stripeConnectGateway.saveTerminalLocationId(branchId, location.id)
        log.info(
            "stripe terminal location created merchantId={} branchId={} locationId={}",
            merchantId, branchId, location.id,
        )
        return location.id
    }
}
