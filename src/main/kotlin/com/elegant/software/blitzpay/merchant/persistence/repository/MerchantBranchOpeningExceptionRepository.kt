package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranchOpeningException
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate
import java.util.UUID

interface MerchantBranchOpeningExceptionRepository : JpaRepository<MerchantBranchOpeningException, UUID> {
    fun findAllByBranchIdAndExceptionDateBetween(
        branchId: UUID, from: LocalDate, to: LocalDate,
    ): List<MerchantBranchOpeningException>
}
