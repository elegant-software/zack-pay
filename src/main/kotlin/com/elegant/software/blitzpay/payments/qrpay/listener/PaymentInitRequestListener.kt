@file:ApplicationModule(displayName = "outbound")

package com.elegant.software.blitzpay.payments.qrpay.listener

import com.elegant.software.blitzpay.payments.truelayer.inbound.TlWebhookEnvelope
import com.elegant.software.blitzpay.order.api.OrderPaymentInitiationRequested
import com.elegant.software.blitzpay.order.api.PaymentMethod
import com.elegant.software.blitzpay.payments.support.PaymentUpdateBus
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.*

@ConditionalOnProperty(name = ["truelayer.enabled"], havingValue = "true")
@Component
class PaymentInitRequestListener(
    private val paymentUpdateBus: PaymentUpdateBus,
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(PaymentInitRequestListener::class.java)
    }

    @EventListener
    fun on(e: TlWebhookEnvelope) {
        val paymentRequestId = e.metadata?.get("paymentRequestId")
        LOG.info(
            "truelayer webhook envelope received type={} eventId={} paymentId={} paymentRequestId={}",
            e.type, e.event_id, e.payment_id, paymentRequestId,
        )
        if (paymentRequestId is String) {
            val uuid = UUID.fromString(paymentRequestId)
            paymentUpdateBus.complete(uuid)
            LOG.info("truelayer payment bus completed paymentRequestId={}", paymentRequestId)
        } else {
            LOG.warn("truelayer webhook envelope missing paymentRequestId in metadata eventId={}", e.event_id)
        }
    }

    @ApplicationModuleListener
    fun on(event: OrderPaymentInitiationRequested) {
        // TRUELAYER requires an interactive redirect: the mobile app calls POST /v1/payments/request
        // which returns paymentId + resourceToken synchronously. Initiating here would create a
        // second concurrent TrueLayer payment session, causing the /v1/payments/request call to fail.
        if (event.paymentMethod == PaymentMethod.TRUELAYER) {
            LOG.info(
                "truelayer payment initiation deferred orderId={} paymentRequestId={} — mobile must call POST /v1/payments/request",
                event.orderId, event.paymentRequestId,
            )
            return
        }
        LOG.info(
            "payment initiation received orderId={} method={} — no auto-handler registered",
            event.orderId, event.paymentMethod,
        )
    }
}
