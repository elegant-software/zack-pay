package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant

@Embeddable
data class ReviewDecision(
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val outcome: ReviewOutcome,

    @Column(nullable = false)
    val reason: String,

    @Column(nullable = false)
    val reviewerId: String,

    @Column(nullable = false)
    val decidedAt: Instant = Instant.now()
) {
    companion object {
        fun actionRequired(
            reason: String,
            reviewerId: String = "system",
            decidedAt: Instant = Instant.now()
        ): ReviewDecision = ReviewDecision(
            outcome = ReviewOutcome.ACTION_REQUIRED,
            reason = reason,
            reviewerId = reviewerId,
            decidedAt = decidedAt
        )
    }
}

enum class ReviewOutcome {
    APPROVED,
    REJECTED,
    ACTION_REQUIRED
}
