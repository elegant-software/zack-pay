package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKiosk
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantKioskRepository : JpaRepository<MerchantKiosk, UUID> {
    fun findByBranchId(branchId: UUID): MerchantKiosk?
    fun findByLogin(login: String): MerchantKiosk?
    fun deleteAllByBranchIdIn(branchIds: Collection<UUID>)
}
