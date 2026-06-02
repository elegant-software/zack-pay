package com.elegant.software.blitzpay.merchant.web

import java.util.UUID

object MerchantTenantContext {
    private val holder = ThreadLocal<UUID?>()

    fun set(merchantId: UUID) { holder.set(merchantId) }
    fun clear() { holder.remove() }
    fun currentMerchantId(): UUID? = holder.get()
}
