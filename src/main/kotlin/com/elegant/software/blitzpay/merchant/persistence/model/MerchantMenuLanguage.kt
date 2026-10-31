package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "merchant_menu_languages", schema = "blitzpay")
class MerchantMenuLanguage(
    @Id
    @Column(nullable = false, length = 8)
    val code: String,

    @Column(name = "display_name", nullable = false, length = 64)
    var displayName: String,

    @Column(name = "native_name", nullable = false, length = 64)
    var nativeName: String = displayName,

    @Column(nullable = false)
    var active: Boolean = true,
)
