package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "merchant_verticals", schema = "blitzpay")
class MerchantVertical(
    @Id
    @Column(nullable = false, length = 64)
    val code: String,

    @Column(name = "display_name", nullable = false, length = 100)
    val displayName: String,

    @Column(nullable = false)
    val active: Boolean = true,
)
