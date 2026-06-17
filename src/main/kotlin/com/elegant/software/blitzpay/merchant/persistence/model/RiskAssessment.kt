package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant

@Embeddable
data class RiskAssessment(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val level: RiskLevel,

    @Column(nullable = false)
    val score: Int,

    @Column(nullable = false)
    val rationale: String,

    @Column(nullable = false)
    val assessedAt: Instant = Instant.now()
)

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
