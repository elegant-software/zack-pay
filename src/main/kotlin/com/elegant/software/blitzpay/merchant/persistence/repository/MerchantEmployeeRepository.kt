package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantEmployee
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import jakarta.persistence.LockModeType
import java.util.UUID

interface MerchantEmployeeRepository : JpaRepository<MerchantEmployee, UUID> {
    fun findAllByMerchantId(merchantId: UUID): List<MerchantEmployee>
    fun findAllByMerchantIdAndStatus(merchantId: UUID, status: String): List<MerchantEmployee>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM MerchantEmployee e WHERE e.id = :id")
    fun findByIdForUpdate(id: UUID): MerchantEmployee?
}
