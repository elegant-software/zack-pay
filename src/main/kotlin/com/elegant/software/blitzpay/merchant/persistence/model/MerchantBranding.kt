package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class MerchantBranding(
    @Column(name = "branding_enabled") var enabled: Boolean? = null,
    @Column(name = "branding_display_name", length = 120) var displayName: String? = null,
    @Column(name = "branding_logo_storage_key", length = 512) var logoStorageKey: String? = null,
    @Column(name = "branding_primary_color", length = 7) var primaryColor: String? = null,
    @Column(name = "branding_accent_color", length = 7) var accentColor: String? = null,
)
