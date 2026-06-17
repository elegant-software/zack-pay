package com.elegant.software.blitzpay.payments.braintree

import com.elegant.software.blitzpay.merchant.api.BraintreeCredentials
import com.elegant.software.blitzpay.payments.braintree.outbound.BraintreeGatewayFactory
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class BraintreeGatewayFactoryTest {

    private val factory = BraintreeGatewayFactory()

    private val sandboxCredentials = BraintreeCredentials(
        merchantId = "merchant1",
        publicKey = "pubkey1",
        privateKey = "privkey1",
        environment = "sandbox",
    )

    @Test
    fun `same credentials return same gateway instance`() {
        val g1 = factory.get(sandboxCredentials)
        val g2 = factory.get(sandboxCredentials)
        assertSame(g1, g2)
    }

    @Test
    fun `different merchant ids produce different gateways`() {
        val other = sandboxCredentials.copy(merchantId = "merchant2")
        val g1 = factory.get(sandboxCredentials)
        val g2 = factory.get(other)
        assertNotSame(g1, g2)
    }

    @Test
    fun `different environments produce different gateways`() {
        val prod = sandboxCredentials.copy(environment = "production")
        val g1 = factory.get(sandboxCredentials)
        val g2 = factory.get(prod)
        assertNotSame(g1, g2)
    }

    @Test
    fun `different public keys produce different gateways`() {
        val other = sandboxCredentials.copy(publicKey = "pubkey2")
        val g1 = factory.get(sandboxCredentials)
        val g2 = factory.get(other)
        assertNotSame(g1, g2)
    }
}
