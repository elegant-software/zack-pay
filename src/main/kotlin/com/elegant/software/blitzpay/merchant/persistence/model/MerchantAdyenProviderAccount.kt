package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchant_adyen_provider_accounts", schema = "blitzpay")
class MerchantAdyenProviderAccount(
    @Id
    @Column(name = "merchant_provider_account_id")
    val merchantProviderAccountId: UUID,

    @Column(name = "legal_entity_id", nullable = false, unique = true, length = 64)
    val legalEntityId: String,

    @Column(name = "account_holder_id", nullable = false, length = 64)
    val accountHolderId: String,

    @Column(name = "balance_account_id", nullable = false, unique = true, length = 64)
    val balanceAccountId: String,

    @Column(name = "receive_payments_enabled", nullable = false)
    var receivePaymentsEnabled: Boolean = false,

    @Column(name = "details_submitted", nullable = false)
    var detailsSubmitted: Boolean = false,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt,
)
