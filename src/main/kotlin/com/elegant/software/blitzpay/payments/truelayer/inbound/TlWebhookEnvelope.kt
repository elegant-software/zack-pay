package com.elegant.software.blitzpay.payments.truelayer.inbound

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
@JsonIgnoreProperties(ignoreUnknown = true)
data class TlWebhookEnvelope(
    val type: String,
    val event_id: String? = null,
    val event_version: Int? = null,
    val timestamp: String? = null,
    val payment_id: String? = null,
    val payment_method: PaymentMethod? = null,
    val executed_at: String? = null,
    val payment_source: PaymentSource? = null,
    val metadata: Map<String, String>? = null
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PaymentMethod(
        val type: String? = null,
        val provider_id: String? = null,
        val scheme_id: String? = null
    )
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PaymentSource(
        val account_identifiers: List<AccountIdentifier>? = null,
        val account_holder_name: String? = null
    )
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AccountIdentifier(
        val type: String? = null,
        val sort_code: String? = null,
        val account_number: String? = null
    )

}

