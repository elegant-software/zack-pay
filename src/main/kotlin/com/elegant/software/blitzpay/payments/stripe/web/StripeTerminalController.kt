package com.elegant.software.blitzpay.payments.stripe.web

import com.elegant.software.blitzpay.config.ApiException
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
import org.springframework.web.bind.annotation.*
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
    fun connectionToken(@RequestBody request: ConnectionTokenRequest): ResponseEntity<Any> {
        val connectedAccountId = stripeConnectGateway.findConnectedAccountId(request.merchantId)
            ?.takeIf { stripeConnectGateway.isChargesEnabled(request.merchantId) }
            ?: throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe Connect account not configured or not yet verified for this merchant")
        if (!stripeConnectGateway.isTapToPayEnabled(request.merchantId)) {
            throw ApiException(HttpStatus.FORBIDDEN, "Tap to Pay is not enabled for this merchant")
        }
        val locationId = resolveOrCreateLocation(request.merchantId, request.branchId, connectedAccountId)
            ?: throw ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Branch not found or missing address — cannot create Stripe Terminal Location")
        return try {
            val requestOptions = RequestOptions.builder().setApiKey(stripeProperties.secretKey).setStripeAccount(connectedAccountId).build()
            val token = ConnectionToken.create(ConnectionTokenCreateParams.builder().setLocation(locationId).build(), requestOptions)
            log.info("stripe terminal connection_token issued merchantId={} branchId={} locationId={}", request.merchantId, request.branchId, locationId)
            ResponseEntity.ok(ConnectionTokenResponse(secret = token.secret, locationId = locationId) as Any)
        } catch (ex: StripeException) {
            log.error("stripe terminal connection_token failed code={} message={}", ex.code, ex.message)
            throw ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message ?: "Stripe error")
        }
    }

    private fun resolveOrCreateLocation(merchantId: UUID, branchId: UUID, connectedAccountId: String): String? {
        stripeConnectGateway.findTerminalLocationId(branchId)?.let { return it }
        val branchInfo = stripeConnectGateway.getBranchTerminalInfo(branchId) ?: return null
        val requestOptions = RequestOptions.builder().setApiKey(stripeProperties.secretKey).setStripeAccount(connectedAccountId).build()
        val addressBuilder = LocationCreateParams.Address.builder()
            .apply { branchInfo.addressLine1?.let { setLine1(it) } }
            .apply { branchInfo.city?.let { setCity(it) } }
            .apply { branchInfo.postalCode?.let { setPostalCode(it) } }
            .setCountry(branchInfo.country ?: "GB")
        val location = Location.create(
            LocationCreateParams.builder().setDisplayName(branchInfo.branchName).setAddress(addressBuilder.build()).build(),
            requestOptions,
        )
        stripeConnectGateway.saveTerminalLocationId(branchId, location.id)
        log.info("stripe terminal location created merchantId={} branchId={} locationId={}", merchantId, branchId, location.id)
        return location.id
    }
}
