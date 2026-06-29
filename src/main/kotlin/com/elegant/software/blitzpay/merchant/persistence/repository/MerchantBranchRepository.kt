package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

interface MerchantBranchRepository : JpaRepository<MerchantBranch, UUID> {
    fun findByNameAndMerchantIdAndActiveTrue(name: String, merchantId: UUID): MerchantBranch?
    fun findByNameAndMerchantId(name: String, merchantId: UUID): MerchantBranch?

    fun findAllByMerchantId(merchantId: UUID): List<MerchantBranch>
    fun findAllByMerchantIdAndActiveTrue(merchantId: UUID): List<MerchantBranch>
    fun findAllByActiveTrue(): List<MerchantBranch>
    fun findAllByMerchantIdInAndActiveTrue(merchantIds: Collection<UUID>): List<MerchantBranch>
    fun findByIdAndActiveTrue(id: UUID): MerchantBranch?
    fun existsByMerchantIdAndIdAndActiveTrue(merchantId: UUID, id: UUID): Boolean
    fun existsByMerchantIdAndId(merchantId: UUID, id: UUID): Boolean
    fun deleteAllByMerchantId(merchantId: UUID)

    @Modifying
    @Transactional
    @Query("UPDATE MerchantBranch b SET b.status = :status WHERE b.id = :id")
    fun updateStatus(@Param("id") id: UUID, @Param("status") status: String): Int

    @Query(
        value = """
            SELECT *
            FROM blitzpay.merchant_branches
            WHERE google_place_id IS NOT NULL
              AND active = TRUE
              AND (place_enrichment_status IS NULL OR place_enrichment_status IN ('PENDING', 'FAILED'))
            ORDER BY updated_at
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findPlaceIdsNeedingEnrichment(@Param("limit") limit: Int): List<MerchantBranch>
}
