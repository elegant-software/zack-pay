package com.elegant.software.blitzpay.merchant.support

interface MerchantObservabilitySupport {
    fun recordSuccess(action: String, status: String)

    fun recordFailure(action: String, reason: String)
}
