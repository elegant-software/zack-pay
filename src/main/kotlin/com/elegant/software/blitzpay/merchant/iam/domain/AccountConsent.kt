package com.elegant.software.blitzpay.merchant.iam.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** An account accepting one version of the Terms, Privacy Policy and DPA (GDPR Art. 7(1) proof of consent). */
@Entity
@Table(name = "account_consents", schema = "blitzpay")
class AccountConsent(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "keycloak_user_id", nullable = false, updatable = false)
    val keycloakUserId: UUID,

    @Column(name = "terms_version", nullable = false, updatable = false, length = 32)
    val termsVersion: String,

    @Column(name = "accepted_at", nullable = false, updatable = false)
    val acceptedAt: Instant = Instant.now(),
)
