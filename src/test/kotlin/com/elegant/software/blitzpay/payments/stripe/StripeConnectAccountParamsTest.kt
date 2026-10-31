package com.elegant.software.blitzpay.payments.stripe

import com.elegant.software.blitzpay.merchant.api.MerchantContactInfo
import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.payments.stripe.web.buildAccountCreateParams
import com.elegant.software.blitzpay.payments.stripe.web.buildAccountLinkCreateParams
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StripeConnectAccountParamsTest {

    private val merchantId = UUID.fromString("8ff7bcf5-e52c-4c25-877e-a32cfc81ea46")

    private fun details(
        operatingCountry: String = "DE",
        website: String? = "https://cafe.example",
        email: String? = "owner@cafe.example",
    ) = MerchantDetailsResponse(
        merchantId = merchantId,
        merchantName = "Cafe Blitz",
        businessType = "COMPANY",
        operatingCountry = operatingCountry,
        website = website,
        lastUpdatedAt = Instant.EPOCH,
        contactInfo = MerchantContactInfo(email = email),
    )

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.at(vararg path: String): Any? =
        path.fold(this as Any?) { node, key -> (node as? Map<String, Any?>)?.get(key) }

    @Test
    fun `creates express v2 account with platform responsibilities and v1-equivalent capabilities`() {
        val map = buildAccountCreateParams(details()).toMap()

        assertEquals("express", map["dashboard"])
        assertEquals("owner@cafe.example", map["contact_email"])
        assertEquals("Cafe Blitz", map["display_name"])
        assertEquals("de", map.at("identity", "country"))
        assertEquals("application", map.at("defaults", "responsibilities", "fees_collector"))
        assertEquals("application", map.at("defaults", "responsibilities", "losses_collector"))
        assertEquals("https://cafe.example", map.at("defaults", "profile", "business_url"))
        assertEquals(true, map.at("configuration", "merchant", "capabilities", "card_payments", "requested"))
        assertEquals(true, map.at("configuration", "merchant", "capabilities", "pay_by_bank_payments", "requested"))
        assertEquals(
            true,
            map.at("configuration", "recipient", "capabilities", "stripe_balance", "stripe_transfers", "requested"),
        )
        assertEquals(merchantId.toString(), map.at("metadata", "merchantId"))
    }

    @Test
    fun `omits identity, email and urls when merchant data is missing or not ISO`() {
        val map = buildAccountCreateParams(details(operatingCountry = "Germany", website = " ", email = null)).toMap()

        assertFalse(map.containsKey("identity"))
        assertFalse(map.containsKey("contact_email"))
        assertEquals(null, map.at("defaults", "profile", "business_url"))
        assertEquals(null, map.at("configuration", "merchant", "support"))
    }

    @Test
    fun `builds v2 onboarding account link`() {
        val map = buildAccountLinkCreateParams("acct_123", "https://app/return", "https://api/refresh").toMap()

        assertEquals("acct_123", map["account"])
        assertEquals("account_onboarding", map.at("use_case", "type"))
        assertEquals("https://app/return", map.at("use_case", "account_onboarding", "return_url"))
        assertEquals("https://api/refresh", map.at("use_case", "account_onboarding", "refresh_url"))
    }
}
