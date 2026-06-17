package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantEmployeeAvailabilityException
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface MerchantEmployeeAvailabilityExceptionRepository : JpaRepository<MerchantEmployeeAvailabilityException, UUID> {
    fun findAllByEmployeeIdAndBranchIdAndExceptionDateBetween(
        employeeId: UUID, branchId: UUID, from: LocalDate, to: LocalDate,
    ): List<MerchantEmployeeAvailabilityException>

    fun findAllByEmployeeIdInAndExceptionDateBetween(
        employeeIds: Collection<UUID>, from: LocalDate, to: LocalDate,
    ): List<MerchantEmployeeAvailabilityException>
}
