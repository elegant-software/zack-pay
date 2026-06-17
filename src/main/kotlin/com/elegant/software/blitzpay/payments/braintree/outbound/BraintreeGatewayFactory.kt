package com.elegant.software.blitzpay.payments.braintree.outbound

import com.braintreegateway.BraintreeGateway
import com.braintreegateway.Environment
import com.elegant.software.blitzpay.merchant.api.BraintreeCredentials
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class BraintreeGatewayFactory {

    private val cache = ConcurrentHashMap<String, BraintreeGateway>()

    fun get(credentials: BraintreeCredentials): BraintreeGateway =
        cache.computeIfAbsent(credentials.fingerprint()) {
            val env = if (credentials.environment.lowercase() == "production") {
                Environment.PRODUCTION
            } else {
                Environment.SANDBOX
            }
            BraintreeGateway(env, credentials.merchantId, credentials.publicKey, credentials.privateKey)
        }

    private fun BraintreeCredentials.fingerprint() = "${merchantId}:${publicKey}:${environment}"
}
