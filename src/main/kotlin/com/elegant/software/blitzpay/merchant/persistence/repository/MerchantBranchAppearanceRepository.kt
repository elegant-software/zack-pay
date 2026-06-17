package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranchAppearance
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface MerchantBranchAppearanceRepository : JpaRepository<MerchantBranchAppearance, UUID> {

    fun findAllByBranchId(branchId: UUID): List<MerchantBranchAppearance>

    @Query("""
        SELECT a FROM MerchantBranchAppearance a
        JOIN a.branch b
        WHERE b.merchantApplicationId = :merchantId
          AND a.cancelledAt IS NULL
          AND a.endTime >= :now
        ORDER BY a.startTime ASC
    """)
    fun findMerchantOperational(
        @Param("merchantId") merchantId: UUID,
        @Param("now") now: Instant,
    ): List<MerchantBranchAppearance>

    @Query("""
        SELECT a FROM MerchantBranchAppearance a
        WHERE a.branchId = :branchId
          AND a.cancelledAt IS NULL
          AND a.endTime >= :now
        ORDER BY a.startTime ASC
    """)
    fun findOperational(
        @Param("branchId") branchId: UUID,
        @Param("now") now: Instant,
    ): List<MerchantBranchAppearance>

    @Query("""
        SELECT a FROM MerchantBranchAppearance a
        WHERE a.branchId = :branchId
          AND a.cancelledAt IS NULL
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
          AND a.cancelledAt IS NULL
    """)
    fun findAllActive(@Param("now") now: Instant): List<MerchantBranchAppearance>

    @Query("""
        SELECT a FROM MerchantBranchAppearance a
        WHERE a.branchId = :branchId
          AND a.startTime >= :from
          AND a.startTime < :to
          AND (a.cancelledAt IS NOT NULL OR (a.cancelledAt IS NULL AND a.endTime < :now))
    """)
    fun findBranchHistory(
        @Param("branchId") branchId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("now") now: Instant,
        pageable: Pageable,
    ): Page<MerchantBranchAppearance>

    @Query("""
        SELECT a FROM MerchantBranchAppearance a
        WHERE a.branchId = :branchId
          AND a.cancelledAt IS NOT NULL
          AND a.startTime >= :from
          AND a.startTime < :to
    """)
    fun findBranchCancelledHistory(
        @Param("branchId") branchId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        pageable: Pageable,
    ): Page<MerchantBranchAppearance>

    @Query("""
        SELECT a FROM MerchantBranchAppearance a
        WHERE a.branchId = :branchId
          AND a.cancelledAt IS NULL
          AND a.endTime < :now
          AND a.startTime >= :from
          AND a.startTime < :to
    """)
    fun findBranchCompletedHistory(
        @Param("branchId") branchId: UUID,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("now") now: Instant,
        pageable: Pageable,
    ): Page<MerchantBranchAppearance>

    @Query("""
        SELECT a FROM MerchantBranchAppearance a
        JOIN a.branch b
        WHERE b.merchantApplicationId = :merchantId
          AND (:branchId IS NULL OR a.branchId = :branchId)
          AND a.startTime >= :from
          AND a.startTime < :to
          AND (a.cancelledAt IS NOT NULL OR (a.cancelledAt IS NULL AND a.endTime < :now))
    """)
    fun findMerchantHistory(
        @Param("merchantId") merchantId: UUID,
        @Param("branchId") branchId: UUID?,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("now") now: Instant,
        pageable: Pageable,
    ): Page<MerchantBranchAppearance>

    @Query("""
        SELECT a FROM MerchantBranchAppearance a
        JOIN a.branch b
        WHERE b.merchantApplicationId = :merchantId
          AND (:branchId IS NULL OR a.branchId = :branchId)
          AND a.cancelledAt IS NOT NULL
          AND a.startTime >= :from
          AND a.startTime < :to
    """)
    fun findMerchantCancelledHistory(
        @Param("merchantId") merchantId: UUID,
        @Param("branchId") branchId: UUID?,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        pageable: Pageable,
    ): Page<MerchantBranchAppearance>

    @Query("""
        SELECT a FROM MerchantBranchAppearance a
        JOIN a.branch b
        WHERE b.merchantApplicationId = :merchantId
          AND (:branchId IS NULL OR a.branchId = :branchId)
          AND a.cancelledAt IS NULL
          AND a.endTime < :now
          AND a.startTime >= :from
          AND a.startTime < :to
    """)
    fun findMerchantCompletedHistory(
        @Param("merchantId") merchantId: UUID,
        @Param("branchId") branchId: UUID?,
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("now") now: Instant,
        pageable: Pageable,
    ): Page<MerchantBranchAppearance>

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM MerchantBranchAppearance a
        WHERE (a.cancelledAt IS NOT NULL AND a.cancelledAt < :cutoff)
           OR (a.cancelledAt IS NULL AND a.endTime < :cutoff)
    """)
    fun deleteHistoryBefore(@Param("cutoff") cutoff: Instant): Int
}
