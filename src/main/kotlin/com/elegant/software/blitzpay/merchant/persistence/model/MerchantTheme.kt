package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import com.elegant.software.blitzpay.merchant.api.ThemeApp
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "merchant_theme", schema = "blitzpay")
class MerchantTheme(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_id", nullable = false, updatable = false) val merchantId: UUID,
    @Column(nullable = false, length = 120) var name: String,
    @Column(name = "primary_color", nullable = false, length = 7) var primaryColor: String,
    @Column(name = "accent_color", nullable = false, length = 7) var accentColor: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16) val app: ThemeApp = ThemeApp.SHOPPER,
)
