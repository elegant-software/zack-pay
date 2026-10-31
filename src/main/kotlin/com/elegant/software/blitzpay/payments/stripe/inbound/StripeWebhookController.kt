package com.elegant.software.blitzpay.payments.stripe.inbound

import com.elegant.software.blitzpay.config.LogContext
import com.elegant.software.blitzpay.merchant.api.StripeConnectGateway
import com.elegant.software.blitzpay.payments.push.api.PaymentStatusUpdateGateway
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.stripe.Stripe
import com.stripe.exception.EventDataObjectDeserializationException
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Account
import com.stripe.model.Event
import com.stripe.model.PaymentIntent
import com.stripe.model.StripeObject
import com.stripe.net.Webhook
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/webhooks/stripe", version = "1")
class StripeWebhookController(
    private val stripeProperties: StripeProperties,
    private val paymentStatusUpdateGateway: PaymentStatusUpdateGateway,
    private val stripeConnectGateway: StripeConnectGateway,
    private val eventPublisher: ApplicationEventPublisher,
) {
    private val log = LoggerFactory.getLogger(StripeWebhookController::class.java)
    private val objectMapper = jacksonObjectMapper()

    @PostMapping
    fun receive(
        @RequestHeader("Stripe-Signature") signature: String?,
        @RequestBody payload: String,
    ): ResponseEntity<Any> {
        return LogContext.with(LogContext.PROVIDER to "STRIPE") {
            log.info("stripe webhook received payloadLength={} signaturePresent={}", payload.length, !signature.isNullOrBlank())
            if (stripeProperties.webhookSecret.isBlank()) {
                log.warn("stripe webhook received but STRIPE_WEBHOOK_SECRET is not configured")
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
            }
            if (signature.isNullOrBlank()) {
                log.warn("stripe webhook rejected reason=missing_signature")
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }

            val event = try {
                Webhook.constructEvent(payload, signature, stripeProperties.webhookSecret)
            } catch (ex: SignatureVerificationException) {
                log.warn("stripe webhook rejected reason=signature_invalid error={}", ex.message)
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
            }

            LogContext.with(LogContext.EVENT_ID to event.id) {
                log.info("stripe webhook verified type={} created={}", event.type, event.created)
                val paymentIntent = extractPaymentIntent(event)
                    ?: run {
                        log.warn(
                            "stripe webhook event={} type={} could not deserialize PaymentIntent; ignoring and skipping payment status update",
                            event.id,
                            event.type,
                        )
                        return ResponseEntity.ok().build()
                    }

                val paymentRequestId = paymentIntent.metadata["paymentRequestId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: run {
                        log.warn("stripe webhook event={} missing paymentRequestId in metadata intentId={}", event.id, paymentIntent.id)
                        return ResponseEntity.ok().build()
                    }

                return LogContext.with(
                    LogContext.PAYMENT_REQUEST_ID to paymentRequestId,
                    LogContext.PAYMENT_INTENT_ID to paymentIntent.id,
                ) {
                    val occurredAt = Instant.ofEpochSecond(event.created)
                    log.info(
                        "stripe webhook mapped paymentRequestId metadataKeys={} occurredAt={}",
                        paymentIntent.metadata.keys, occurredAt,
                    )

                    when (event.type) {
                        "payment_intent.succeeded" -> {
                            log.info("stripe webhook settling payment")
                            paymentStatusUpdateGateway.settle(paymentRequestId, "stripe:${event.id}", occurredAt)
                            val merchantId = paymentIntent.metadata["merchantId"]
                                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            val connectedAccountId = merchantId?.let {
                                stripeConnectGateway.findConnectedAccountId(it)
                            }
                            eventPublisher.publishEvent(
                                StripePaymentSettled(
                                    paymentRequestId = paymentRequestId,
                                    stripePaymentIntentId = paymentIntent.id,
                                    stripeEventId = event.id,
                                    connectedAccountId = connectedAccountId,
                                    grossAmountMinor = paymentIntent.amount ?: 0L,
                                    currency = paymentIntent.currency?.uppercase() ?: "EUR",
                                    applicationFeeAmountMinor = paymentIntent.applicationFeeAmount,
                                    occurredAt = occurredAt,
                                )
                            )
                        }
                        "payment_intent.payment_failed", "payment_intent.canceled" -> {
                            log.info("stripe webhook failing payment type={}", event.type)
                            paymentStatusUpdateGateway.fail(paymentRequestId, "stripe:${event.id}", occurredAt)
                        }
                        else -> log.debug("stripe webhook event={} type={} not handled", event.id, event.type)
                    }

                    ResponseEntity.ok().build()
                }
            }
        }
    }

    @PostMapping("/connect")
    fun receiveConnect(
        @RequestHeader("Stripe-Signature") signature: String?,
        @RequestBody payload: String,
    ): ResponseEntity<Any> {
        log.info("stripe connect webhook received payloadLength={} signaturePresent={}", payload.length, !signature.isNullOrBlank())
        if (stripeProperties.connectWebhookSecret.isBlank()) {
            log.warn("stripe connect webhook received but STRIPE_CONNECT_WEBHOOK_SECRET is not configured")
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }
        if (signature.isNullOrBlank()) {
            log.warn("stripe connect webhook rejected reason=missing_signature")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        val event = try {
            Webhook.constructEvent(payload, signature, stripeProperties.connectWebhookSecret)
        } catch (ex: SignatureVerificationException) {
            log.warn("stripe connect webhook rejected reason=signature_invalid error={}", ex.message)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
        when (event.type) {
            "account.updated" -> {
                val account = extractDataObject<Account>(event)
                if (account != null) {
                    log.info(
                        "stripe connect account.updated connectedAccountId={} chargesEnabled={} detailsSubmitted={} cardPayments={} payByBankPayments={}",
                        account.id,
                        account.chargesEnabled,
                        account.detailsSubmitted,
                        account.capabilities?.cardPayments,
                        capabilityStatus(payload, "pay_by_bank_payments"),
                    )
                    val cardPaymentsActive = account.capabilities?.cardPayments == "active"
                    val payByBankActive = capabilityStatus(payload, "pay_by_bank_payments") == "active"
                    stripeConnectGateway.onAccountUpdated(
                        connectedAccountId = account.id,
                        chargesEnabled = account.chargesEnabled && cardPaymentsActive,
                        detailsSubmitted = account.detailsSubmitted,
                        payByBankEnabled = payByBankActive,
                    )
                } else {
                    log.warn("stripe connect account.updated could not deserialize Account object eventId={}", event.id)
                }
            }
            "payment_intent.succeeded", "payment_intent.payment_failed", "payment_intent.canceled" -> {
                val paymentIntent = extractPaymentIntent(event)
                if (paymentIntent == null) {
                    log.warn(
                        "stripe connect webhook event={} type={} could not deserialize PaymentIntent; ignoring",
                        event.id, event.type,
                    )
                    return ResponseEntity.ok().build()
                }
                val paymentRequestId = paymentIntent.metadata["paymentRequestId"]
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                if (paymentRequestId == null) {
                    log.warn("stripe connect webhook event={} missing paymentRequestId in metadata intentId={}", event.id, paymentIntent.id)
                    return ResponseEntity.ok().build()
                }
                val occurredAt = Instant.ofEpochSecond(event.created)
                log.info(
                    "stripe connect webhook type={} paymentRequestId={} intentId={} occurredAt={}",
                    event.type, paymentRequestId, paymentIntent.id, occurredAt,
                )
                when (event.type) {
                    "payment_intent.succeeded" -> {
                        paymentStatusUpdateGateway.settle(paymentRequestId, "stripe:${event.id}", occurredAt)
                        eventPublisher.publishEvent(
                            StripePaymentSettled(
                                paymentRequestId = paymentRequestId,
                                stripePaymentIntentId = paymentIntent.id,
                                stripeEventId = event.id,
                                connectedAccountId = event.account,
                                grossAmountMinor = paymentIntent.amount ?: 0L,
                                currency = paymentIntent.currency?.uppercase() ?: "EUR",
                                applicationFeeAmountMinor = paymentIntent.applicationFeeAmount,
                                occurredAt = occurredAt,
                            )
                        )
                    }
                    "payment_intent.payment_failed", "payment_intent.canceled" -> paymentStatusUpdateGateway.fail(paymentRequestId, "stripe:${event.id}", occurredAt)
                }
            }
            else -> log.debug("stripe connect webhook event={} type={} not handled", event.id, event.type)
        }
        return ResponseEntity.ok().build()
    }

    private fun extractPaymentIntent(event: Event): PaymentIntent? = extractDataObject<PaymentIntent>(event)

    /**
     * Each stripe-java major pins one Stripe API release (34.x = `2026-10-28.endive`). When the webhook
     * endpoint delivers events at a different release, `getObject()` returns empty, so fall back to
     * `deserializeUnsafe()` and only give up if the payload really cannot be mapped.
     */
    private inline fun <reified T : StripeObject> extractDataObject(event: Event): T? {
        val deserializer = event.dataObjectDeserializer
        (deserializer.getObject().orElse(null) as? T)?.let { return it }

        return try {
            (deserializer.deserializeUnsafe() as? T)?.also {
                log.warn(
                    "stripe webhook event={} type={} eventApiVersion={} sdkApiVersion={} deserialized {} via unsafe fallback; align the webhook endpoint api_version",
                    event.id, event.type, event.apiVersion, Stripe.API_VERSION, T::class.simpleName,
                )
            }
        } catch (ex: EventDataObjectDeserializationException) {
            log.warn(
                "stripe webhook event={} type={} eventApiVersion={} sdkApiVersion={} failed {} deserialization error={}",
                event.id, event.type, event.apiVersion, Stripe.API_VERSION, T::class.simpleName, ex.message,
            )
            null
        }
    }

    private fun capabilityStatus(payload: String, capabilityCode: String): String? =
        runCatching {
            objectMapper.readTree(payload)
                .path("data")
                .path("object")
                .path("capabilities")
                .path(capabilityCode)
                .takeIf { !it.isMissingNode && !it.isNull }
                ?.asText()
        }.getOrNull()
}
