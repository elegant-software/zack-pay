package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchant_stripe_provider_accounts", schema = "blitzpay")
class MerchantStripeProviderAccount(
    @Id
    @Column(name = "merchant_provider_account_id")
    val merchantProviderAccountId: UUID,

    @Column(name = "connected_account_id", nullable = false, unique = true)
    val connectedAccountId: String,

    @Column(name = "charges_enabled", nullable = false)
    var chargesEnabled: Boolean = false,

    @Column(name = "details_submitted", nullable = false)
    var detailsSubmitted: Boolean = false,

    @Column(name = "pay_by_bank_enabled", nullable = false)
    var payByBankEnabled: Boolean = false,

    @Column(name = "tap_to_pay_enabled", nullable = false)
    var tapToPayEnabled: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
)
