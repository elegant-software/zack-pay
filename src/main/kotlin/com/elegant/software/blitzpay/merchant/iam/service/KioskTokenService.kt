package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.config.KIOSK_TOKEN_PREFIX
import com.elegant.software.blitzpay.config.KioskPrincipal
import com.elegant.software.blitzpay.config.KioskTokenVerifier
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKioskToken
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskTokenRepository
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Issues and verifies the opaque Kiosk tokens. A token is 32 random bytes behind [KIOSK_TOKEN_PREFIX]; only its SHA-256
 * hash is stored, so the database never holds a usable token. It is scoped to one Branch and dies with the Kiosk Login.
 */
@Service
class KioskTokenService(
    private val tokens: MerchantKioskTokenRepository,
    private val kiosks: MerchantKioskRepository,
) : KioskTokenVerifier {
    private val random = SecureRandom()

    fun issue(merchantId: UUID, branchId: UUID): String {
        val token = KIOSK_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))
        tokens.save(MerchantKioskToken(merchantId = merchantId, branchId = branchId, tokenHash = hash(token), expiresAt = Instant.now().plus(TTL)))
        return token
    }

    override fun verify(token: String): KioskPrincipal? {
        val row = tokens.findByTokenHash(hash(token)) ?: return null
        if (!row.expiresAt.isAfter(Instant.now())) return null
        if (kiosks.findByBranchId(row.branchId)?.enabled != true) return null
        return KioskPrincipal(row.merchantId, row.branchId)
    }

    companion object {
        // ponytail: a Kiosk signs in once at setup, so tokens are long-lived; disabling the login revokes them early.
        val TTL: java.time.Duration = java.time.Duration.ofDays(365)

        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
