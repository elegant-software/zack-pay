package com.elegant.software.blitzpay.merchant.iam.service

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKiosk
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKioskToken
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantKioskTokenRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KioskTokenServiceTest {
    private val tokens = mock<MerchantKioskTokenRepository>()
    private val kiosks = mock<MerchantKioskRepository>()
    private val service = KioskTokenService(tokens, kiosks)
    private val merchantId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()

    private fun issueAndStore(): String {
        whenever(tokens.save(any<MerchantKioskToken>())).thenAnswer { it.arguments[0] }
        val token = service.issue(merchantId, branchId)
        val saved = argumentCaptor<MerchantKioskToken>().also { verify(tokens).save(it.capture()) }.firstValue
        whenever(tokens.findByTokenHash(saved.tokenHash)).thenReturn(saved)
        return token
    }

    @Test
    fun `only the hash of the token is stored`() {
        val token = issueAndStore()
        val saved = argumentCaptor<MerchantKioskToken>().also { verify(tokens).save(it.capture()) }.firstValue

        assertTrue(token.startsWith("kt_"))
        assertNotEquals(token, saved.tokenHash)
        assertEquals(KioskTokenService.hash(token), saved.tokenHash)
    }

    @Test
    fun `a valid token of an enabled kiosk resolves to exactly its branch`() {
        val token = issueAndStore()
        whenever(kiosks.findByBranchId(branchId)).thenReturn(MerchantKiosk(branchId = branchId, enabled = true))

        val principal = service.verify(token)!!

        assertEquals(branchId, principal.branchId)
        assertEquals(merchantId, principal.merchantId)
    }

    @Test
    fun `unknown, disabled and expired tokens are rejected`() {
        val token = issueAndStore()
        assertNull(service.verify("kt_unknown"))

        whenever(kiosks.findByBranchId(branchId)).thenReturn(MerchantKiosk(branchId = branchId, enabled = false))
        assertNull(service.verify(token))

        val expired = MerchantKioskToken(merchantId = merchantId, branchId = branchId, tokenHash = KioskTokenService.hash("kt_old"), expiresAt = Instant.now().minusSeconds(1))
        whenever(tokens.findByTokenHash(expired.tokenHash)).thenReturn(expired)
        whenever(kiosks.findByBranchId(branchId)).thenReturn(MerchantKiosk(branchId = branchId, enabled = true))
        assertNull(service.verify("kt_old"))
    }
}
