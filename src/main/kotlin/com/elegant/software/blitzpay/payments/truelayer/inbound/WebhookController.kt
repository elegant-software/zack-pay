
package com.elegant.software.blitzpay.payments.truelayer.inbound


import TlWebhookEnvelope

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.payments.truelayer.support.JwksService
import com.elegant.software.blitzpay.payments.truelayer.support.TlWebhookProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.truelayer.signing.Verifier
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException


@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/webhooks/truelayer", version = "1")
class TlWebhookController(
    private val jwksService: JwksService,
    private val props: TlWebhookProperties,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(TlWebhookController::class.java)
    }
    private val mapper = jacksonObjectMapper()
    private val maxSkew = Duration.parse(props.maxSkew)

    @PostMapping
    fun receive(
        @RequestHeader headers: Map<String, String>,
        @RequestBody rawBody: String
    ): ResponseEntity<Any> {
        val webhookId = headers["x-tl-webhook-id"]
        return LogContext.with(
            LogContext.WEBHOOK_ID to webhookId,
            LogContext.PROVIDER to "TRUELAYER",
        ) {
            handle(headers, rawBody, webhookId)
        }
    }

    private fun handle(
        headers: Map<String, String>,
        rawBody: String,
        webhookId: String?,
    ): ResponseEntity<Any> {
        LOG.info(
            "truelayer webhook received payloadLength={} timestamp={} signaturePresent={}",
            rawBody.length, headers["x-tl-webhook-timestamp"], headers["tl-signature"] != null,
        )
        LOG.debug("truelayer webhook raw body={}", rawBody)
        val signature = headers["tl-signature"]
            ?: run {
                LOG.warn("truelayer webhook rejected reason=missing_tl_signature webhookId={}", webhookId)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }

        // Optional: basic replay protection using X-TL-Webhook-Timestamp
        val tsHeader = headers["x-tl-webhook-timestamp"]
        if (!timestampFresh(tsHeader)) {
            LOG.warn("truelayer webhook rejected reason=stale_timestamp webhookId={} timestamp={} maxSkew={}", webhookId, tsHeader, maxSkew)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val path = "/v1/webhooks/truelayer" // must match exactly
        // Verify JKU is an allowed TrueLayer origin then fetch JWKS
        val jkuFromSig = try {
            Verifier.extractJku(signature)
        } catch (ex: Exception) {
            LOG.warn("truelayer webhook rejected reason=jku_extract_failed webhookId={} error={}", webhookId, ex.message, ex)
            null
        }
        if (jkuFromSig == null || !jkuFromSig.equals(props.allowedJku, ignoreCase = true)) {
            LOG.warn("truelayer webhook rejected reason=jku_mismatch webhookId={} got={} expected={}", webhookId, jkuFromSig, props.allowedJku)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val jwksJson = try {
            jwksService.fetchJwks()
        } catch (ex: Exception) {
            LOG.error("truelayer webhook rejected reason=jwks_fetch_failed webhookId={} jku={}", webhookId, jkuFromSig, ex)
            null
        } ?: run {
            LOG.warn("truelayer webhook rejected reason=jwks_empty webhookId={} jku={}", webhookId, jkuFromSig)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val ok = try {
            Verifier.verifyWithJwks(jwksJson)
                .method("POST")
                .path(path)
                .headers(headers) // header names may be any casing
                .body(rawBody)
                .verify(signature)
            true
        } catch (ex: Exception) {
            LOG.warn("truelayer webhook rejected reason=signature_verification_failed webhookId={} error={}", webhookId, ex.message, ex)
            false
        }

        if (!ok) {
            LOG.warn("truelayer webhook rejected reason=signature_invalid webhookId={}", webhookId)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        // Parse body now that it’s verified
        val event: TlWebhookEnvelope = try {
            mapper.readValue(rawBody)
        } catch (ex: Exception) {
            LOG.warn("truelayer webhook rejected reason=malformed_body webhookId={} error={}", webhookId, ex.message, ex)
            return ResponseEntity.badRequest().build()
        }

        return LogContext.with(
            LogContext.EVENT_ID to event.event_id,
            LogContext.PAYMENT_REQUEST_ID to event.metadata?.get("paymentRequestId"),
        ) {
            LOG.info(
                "truelayer webhook accepted type={} paymentRequestId={} timestamp={} metadataKeys={}",
                event.type, event.metadata?.get("paymentRequestId"), event.timestamp, event.metadata?.keys
            )
            applicationEventPublisher.publishEvent(event)
            LOG.info("truelayer webhook published envelope event")
            ResponseEntity.ok().build()
        }
    }

    private fun timestampFresh(ts: String?): Boolean {
        if (ts == null) return false
        return try {
            val sent = Instant.parse(ts) // e.g. 2020-05-18T10:17:47Z
            val now = Instant.now()
            Duration.between(sent, now).abs() <= maxSkew
        } catch (_: DateTimeParseException) {
            false
        }
    }


}
