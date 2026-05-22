package com.elegant.software.blitzpay.merchant.repository

import com.elegant.software.blitzpay.merchant.domain.MerchantBranchAppearance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface MerchantBranchAppearanceRepository : JpaRepository<MerchantBranchAppearance, UUID> {

    fun findAllByBranchId(branchId: UUID): List<MerchantBranchAppearance>

    @Query("""
        SELECT a FROM MerchantBranchAppearance a
        WHERE a.branchId = :branchId
          AND a.startTime < :endTime
          AND a.endTime > :startTime
    """)
    fun findOverlapping(
        @Param("branchId") branchId: UUID,
        @Param("startTime") startTime: Instant,
        @Param("endTime") endTime: Instant,
    ): List<MerchantBranchAppearance>

    @Query("""
        SELECT a FROM MerchantBranchAppearance a
        WHERE a.startTime <= :now
          AND a.endTime >= :now
    """)
    fun findAllActive(@Param("now") now: Instant): List<MerchantBranchAppearance>
}
