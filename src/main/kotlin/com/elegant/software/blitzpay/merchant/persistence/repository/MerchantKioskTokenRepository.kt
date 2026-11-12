package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKioskToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantKioskTokenRepository : JpaRepository<MerchantKioskToken, UUID> {
    fun findByTokenHash(tokenHash: String): MerchantKioskToken?
    fun deleteAllByBranchId(branchId: UUID)
    fun deleteAllByBranchIdIn(branchIds: Collection<UUID>)
}
