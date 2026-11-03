package com.elegant.software.blitzpay.merchant.iam.service

import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64

/**
 * Generates Kitchen Login passwords: 24 random bytes from [SecureRandom], base64url-encoded without padding
 * (32 URL-safe characters, 192 bits). Regenerates until upper case, lower case, a digit and a symbol (`-` or `_`)
 * are all present, so common Keycloak password policies accept it.
 */
@Component
class KitchenPasswordGenerator(private val random: SecureRandom) {

    constructor() : this(SecureRandom())


    fun generate(): String {
        while (true) {
            val bytes = ByteArray(RANDOM_BYTES).also(random::nextBytes)
            val candidate = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            if (candidate.any(Char::isUpperCase) && candidate.any(Char::isLowerCase) &&
                candidate.any(Char::isDigit) && candidate.any { it == '-' || it == '_' }
            ) return candidate
        }
    }

    private companion object {
        const val RANDOM_BYTES = 24
    }
}
