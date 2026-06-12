package com.elegant.software.blitzpay.config

import org.slf4j.MDC

/**
 * Structured-logging MDC helper. Every field pushed here becomes a top-level
 * JSON key in each log event (see `logback-spring.xml` → `<mdc/>`).
 *
 * Usage:
 * ```
 * LogContext.with("paymentRequestId" to id, "eventId" to eventId) {
 *     // work — every log inside gets both fields attached
 * }
 * ```
 *
 * Safely restores any previously-set value (re-entrant).
 */
object LogContext {
    const val REQUEST_ID = "requestId"
    const val TRACE_ID = "traceId"
    const val SPAN_ID = "spanId"
    const val PAYMENT_REQUEST_ID = "paymentRequestId"
    const val EVENT_ID = "eventId"
    const val WEBHOOK_ID = "webhookId"
    const val ORDER_ID = "orderId"
    const val PROVIDER = "provider"
    const val PAYMENT_INTENT_ID = "paymentIntentId"

    inline fun <T> with(vararg entries: Pair<String, Any?>, block: () -> T): T {
        val prior = HashMap<String, String?>(entries.size)
        for ((k, v) in entries) {
            prior[k] = MDC.get(k)
            if (v != null) MDC.put(k, v.toString()) else MDC.remove(k)
        }
        try {
            return block()
        } finally {
            for ((k, v) in prior) {
                if (v != null) MDC.put(k, v) else MDC.remove(k)
            }
        }
    }
}
