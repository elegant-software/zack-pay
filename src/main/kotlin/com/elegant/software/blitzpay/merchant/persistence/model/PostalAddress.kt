package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class PostalAddress(
    @Column(name = "address_line1")
    val addressLine1: String? = null,

    @Column(name = "address_line2")
    val addressLine2: String? = null,

    @Column(name = "city")
    val city: String? = null,

    @Column(name = "postal_code")
    val postalCode: String? = null,

    @Column(name = "country", length = 2)
    val country: String? = null,
)
