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

    @Column(name = "flag_code", length = 2)
    var flagCode: String? = null,

    @Column(nullable = false, length = 3)
    var direction: String = "ltr",

    @Column(name = "admin_ui_enabled", nullable = false)
    var adminUiEnabled: Boolean = false,

    @Column(name = "menu_enabled", nullable = false)
    var menuEnabled: Boolean = true,
)
