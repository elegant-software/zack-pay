package com.elegant.software.blitzpay.merchant.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "payment_service_providers", schema = "blitzpay")
class PaymentServiceProvider(
    @Id
    @Column(name = "code", length = 32)
    val code: String,

    @Column(name = "display_name", length = 128, nullable = false)
    val displayName: String,
)
