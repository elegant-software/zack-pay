package com.elegant.software.blitzpay.merchant.iam.repository

import com.elegant.software.blitzpay.merchant.iam.domain.AccountConsent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AccountConsentRepository : JpaRepository<AccountConsent, UUID> {
    fun findAllByKeycloakUserId(keycloakUserId: UUID): List<AccountConsent>
    fun deleteAllByKeycloakUserId(keycloakUserId: UUID)
}
