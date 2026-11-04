package com.elegant.software.blitzpay.merchant.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableCodeGeneratorTest {

    @Test
    fun `codes are 6 characters from the alphabet without look-alikes`() {
        repeat(2_000) {
            val code = TableCodeGenerator.generate()
            assertEquals(6, code.length)
            assertTrue(code.all { it in TableCodeGenerator.ALPHABET }, code)
            assertTrue(TableCodeGenerator.isWellFormed(code))
        }
        listOf('0', 'O', '1', 'I', 'L').forEach { assertFalse(it in TableCodeGenerator.ALPHABET) }
    }

    @Test
    fun `codes are random enough not to repeat in a small sample`() {
        val codes = (1..5_000).map { TableCodeGenerator.generate() }.toSet()
        assertTrue(codes.size > 4_990)
    }

    @Test
    fun `unique retries on a collision until a free code comes up`() {
        var calls = 0
        val code = TableCodeGenerator.unique { calls++ < 3 }
        assertEquals(4, calls)
        assertTrue(TableCodeGenerator.isWellFormed(code))
    }

    @Test
    fun `unique gives up after the maximum number of attempts`() {
        assertFailsWith<IllegalStateException> { TableCodeGenerator.unique(maxAttempts = 5) { true } }
    }

    @Test
    fun `normalize upper-cases and trims scanned or typed codes`() {
        assertEquals("K7M2QX", TableCodeGenerator.normalize(" k7m2qx "))
        assertFalse(TableCodeGenerator.isWellFormed("K7M2Q0"))
    }
}
