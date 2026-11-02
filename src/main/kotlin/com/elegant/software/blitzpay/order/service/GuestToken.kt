package com.elegant.software.blitzpay.order.service

import com.elegant.software.blitzpay.order.api.OrderResponse
import com.elegant.software.blitzpay.order.persistence.model.Order
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.HexFormat

data class GuestOrderCreated(val order: OrderResponse, val token: String)

/** Order-scoped capability token for web storefront guests: 256 random bits, only the SHA-256 hash is persisted. */
internal object GuestToken {
    val TTL: Duration = Duration.ofMinutes(60)
    private val random = SecureRandom()

    fun generate(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    fun hash(token: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    fun matches(order: Order, token: String?, now: Instant = Instant.now()): Boolean {
        val expected = order.guestTokenHash ?: return false
        val expiresAt = order.guestTokenExpiresAt ?: return false
        if (token.isNullOrBlank() || now.isAfter(expiresAt)) return false
        return MessageDigest.isEqual(expected.toByteArray(), hash(token).toByteArray())
    }
}
