package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

@Embeddable
data class Person(
    @Column(nullable = false)
    val fullName: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: PersonRole,

    @Column(nullable = false)
    val countryOfResidence: String,

    @Column(nullable = false)
    val ownershipPercentage: Int = 0
)

enum class PersonRole {
    BENEFICIAL_OWNER,
    DIRECTOR,
    PRIMARY_CONTACT,
    AUTHORIZED_REPRESENTATIVE
}
