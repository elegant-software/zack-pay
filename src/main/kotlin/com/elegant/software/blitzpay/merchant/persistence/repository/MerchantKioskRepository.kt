package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKiosk
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantKioskSettings
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantKioskRepository : JpaRepository<MerchantKiosk, UUID> {
    fun findAllByBranchIdOrderByNumber(branchId: UUID): List<MerchantKiosk>
    fun findByIdAndBranchId(id: UUID, branchId: UUID): MerchantKiosk?
    fun findTopByBranchIdOrderByNumberDesc(branchId: UUID): MerchantKiosk?
    fun existsByUsername(username: String): Boolean
    fun deleteAllByBranchIdIn(branchIds: Collection<UUID>)
}

interface MerchantKioskSettingsRepository : JpaRepository<MerchantKioskSettings, UUID> {
    fun findByBranchId(branchId: UUID): MerchantKioskSettings?
    fun deleteAllByBranchIdIn(branchIds: Collection<UUID>)
}
