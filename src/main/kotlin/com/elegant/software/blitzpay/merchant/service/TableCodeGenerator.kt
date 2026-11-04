package com.elegant.software.blitzpay.merchant.service

import java.security.SecureRandom

/**
 * Table Codes: 6 characters from an alphabet without look-alikes (no 0/O, 1/I/L), so a code read off a sticker
 * can be typed back. 31^6 ≈ 887 million codes keeps collisions rare; callers still retry on one.
 */
object TableCodeGenerator {
    const val ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
    const val LENGTH = 6
    private val random = SecureRandom()
    private val pattern = Regex("^[$ALPHABET]{$LENGTH}$")

    fun generate(): String = buildString(LENGTH) { repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

    /** Upper-cased and trimmed, so links typed or scanned in lower case still resolve. */
    fun normalize(code: String): String = code.trim().uppercase()

    fun isWellFormed(code: String): Boolean = pattern.matches(code)

    /** A code not yet used: draws until [isTaken] says no, up to [maxAttempts] times. */
    fun unique(maxAttempts: Int = 10, isTaken: (String) -> Boolean): String {
        repeat(maxAttempts) {
            val code = generate()
            if (!isTaken(code)) return code
        }
        throw IllegalStateException("Could not generate a unique Table Code after $maxAttempts attempts")
    }
}
