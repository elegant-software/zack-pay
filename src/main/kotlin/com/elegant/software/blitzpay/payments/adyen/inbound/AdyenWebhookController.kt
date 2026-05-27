package com.elegant.software.blitzpay.payments.adyen.inbound

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.elegant.software.blitzpay.merchant.api.AdyenConnectGateway
import com.elegant.software.blitzpay.payments.adyen.security.AdyenHmacVerifier
import com.elegant.software.blitzpay.payments.adyen.security.AdyenWebhookSignatureException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Receives Adyen Balance Platform configuration webhooks.
 *
 * Adyen sends HMAC-signed POST requests for account lifecycle events.
 * This controller verifies the HMAC signature and processes
 * `balancePlatform.accountHolder.updated` events to track when a merchant's
 * `receivePayments` capability transitions to `allowed`.
 *
 * Returns `[accepted]` as Adyen expects for all successfully processed webhooks.
 */
@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/webhooks/adyen", version = "1")
class AdyenWebhookController(
    private val adyenHmacVerifier: AdyenHmacVerifier,
    private val adyenConnectGateway: AdyenConnectGateway,
) {
    private val log = LoggerFactory.getLogger(AdyenWebhookController::class.java)
    private val objectMapper = jacksonObjectMapper()

    @PostMapping
    fun receive(
        @RequestHeader("HmacSignature", required = false) hmacSignature: String?,
        @RequestBody payload: String,
    ): ResponseEntity<String> {
        log.info("adyen webhook received payloadLength={} signaturePresent={}", payload.length, !hmacSignature.isNullOrBlank())

        if (hmacSignature.isNullOrBlank()) {
            log.warn("adyen webhook rejected reason=missing_signature")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        try {
            adyenHmacVerifier.verify(hmacSignature, payload)
        } catch (ex: AdyenWebhookSignatureException) {
            log.warn("adyen webhook rejected reason=signature_invalid error={}", ex.message)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val tree = try {
            objectMapper.readTree(payload)
        } catch (ex: Exception) {
            log.warn("adyen webhook rejected reason=invalid_json error={}", ex.message)
            return ResponseEntity.badRequest().build()
        }

        val eventType = tree.path("type").asText()
        log.info("adyen webhook verified type={}", eventType)

        when (eventType) {
            "balancePlatform.accountHolder.updated" -> handleAccountHolderUpdated(tree)
            "balancePlatform.balanceAccount.updated" -> handleBalanceAccountUpdated(tree)
            else -> log.debug("adyen webhook type={} not handled", eventType)
        }

        return ResponseEntity.ok("[accepted]")
    }

    /**
     * Handles `balancePlatform.accountHolder.updated` — checks whether the
     * `receivePayments` capability has transitioned to `allowed` and updates the
     * merchant provider account record accordingly.
     */
    private fun handleAccountHolderUpdated(tree: com.fasterxml.jackson.databind.JsonNode) {
        val accountHolder = tree.path("data").path("accountHolder")
        val accountHolderId = accountHolder.path("id").asText(null) ?: run {
            log.warn("adyen webhook accountHolder.updated missing accountHolder.id")
            return
        }

        val primaryBalanceAccountId = accountHolder.path("primaryBalanceAccount").asText(null)
        val capabilities = accountHolder.path("capabilities")
        val receivePaymentsAllowed = capabilities
            .path("receivePayments")
            .path("allowed")
            .asBoolean(false)
        val detailsSubmitted = capabilities
            .path("receivePayments")
            .path("verificationStatus")
            .asText("")
            .let { it == "valid" || it == "pending" }

        if (primaryBalanceAccountId != null) {
            log.info(
                "adyen webhook accountHolder.updated accountHolderId={} balanceAccountId={} receivePaymentsAllowed={}",
                accountHolderId, primaryBalanceAccountId, receivePaymentsAllowed,
            )
            adyenConnectGateway.onAccountUpdated(
                balanceAccountId = primaryBalanceAccountId,
                receivePaymentsEnabled = receivePaymentsAllowed,
                detailsSubmitted = detailsSubmitted,
            )
        } else {
            log.debug("adyen webhook accountHolder.updated accountHolderId={} no primaryBalanceAccount — skipping", accountHolderId)
        }
    }

    /**
     * Handles `balancePlatform.balanceAccount.updated` — resolves the balance account
     * and updates its status. The `receivePayments` capability lives on the AccountHolder,
     * so this event is handled by looking up the balance account ID and delegating to the gateway.
     */
    private fun handleBalanceAccountUpdated(tree: com.fasterxml.jackson.databind.JsonNode) {
        val balanceAccount = tree.path("data").path("balanceAccount")
        val balanceAccountId = balanceAccount.path("id").asText(null) ?: run {
            log.warn("adyen webhook balanceAccount.updated missing balanceAccount.id")
            return
        }
        log.info("adyen webhook balanceAccount.updated balanceAccountId={}", balanceAccountId)
        // Balance account updates do not carry capability info directly.
        // Capability-driven updates are handled via accountHolder.updated.
    }
}
