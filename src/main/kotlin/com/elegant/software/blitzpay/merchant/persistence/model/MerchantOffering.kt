package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "merchant_offerings", schema = "blitzpay")
class MerchantOffering(
    @Id
    @Column(nullable = false, length = 64)
    val code: String,

    @Column(name = "display_name", nullable = false, length = 255)
    var displayName: String,

    @Column(nullable = false)
    var active: Boolean = true,
)
