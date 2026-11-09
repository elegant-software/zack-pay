package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.domain.Pageable
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID
import java.time.Instant

interface MerchantProductRepository : JpaRepository<MerchantProduct, UUID> {
    @Query("""
        select p from MerchantProduct p
        where p.merchantId = :merchantId and p.merchantBranchId = :branchId
          and (:includeInactive = true or p.active = true)
          and (:categoryId is null or p.productCategoryId = :categoryId)
          and (:uncategorized = false or p.productCategoryId is null)
          and (cast(:afterCreatedAt as timestamp) is null or p.createdAt > :afterCreatedAt
               or (p.createdAt = :afterCreatedAt and p.id > :afterId))
        order by p.createdAt asc, p.id asc
    """)
    fun findCatalogPage(
        @Param("merchantId") merchantId: UUID,
        @Param("branchId") branchId: UUID,
        @Param("categoryId") categoryId: UUID?,
        @Param("uncategorized") uncategorized: Boolean,
        @Param("includeInactive") includeInactive: Boolean,
        @Param("afterCreatedAt") afterCreatedAt: Instant?,
        @Param("afterId") afterId: UUID?,
        pageable: Pageable,
    ): List<MerchantProduct>

    @Query("""
        select p.productCategoryId as categoryId, count(p) as productCount
        from MerchantProduct p
        where p.merchantId = :merchantId and p.merchantBranchId = :branchId
          and (:includeInactive = true or p.active = true)
        group by p.productCategoryId
    """)
    fun catalogCategoryCounts(
        @Param("merchantId") merchantId: UUID,
        @Param("branchId") branchId: UUID,
        @Param("includeInactive") includeInactive: Boolean,
    ): List<CatalogCategoryCount>

    @Modifying
    @Query("""
        update MerchantProduct p set p.thumbnailStorageKey = :thumbnailKey
        where p.id = :productId and p.merchantId = :merchantId
          and p.imageStorageKey = :sourceKey and p.updatedAt = :updatedAt
          and p.thumbnailStorageKey is null
    """)
    fun attachCatalogThumbnail(
        @Param("productId") productId: UUID,
        @Param("merchantId") merchantId: UUID,
        @Param("sourceKey") sourceKey: String,
        @Param("updatedAt") updatedAt: Instant,
        @Param("thumbnailKey") thumbnailKey: String,
    ): Int
    fun findByNameAndMerchantIdAndMerchantBranchIdAndActiveTrue(
        name: String,
        merchantId: UUID,
        merchantBranchId: UUID
    ): MerchantProduct?

    fun findByNameAndMerchantIdAndMerchantBranchId(
        name: String,
        merchantId: UUID,
        merchantBranchId: UUID
    ): MerchantProduct?

    @Modifying
    @Transactional
    @Query("UPDATE MerchantProduct p SET p.status = :status WHERE p.id = :id")
    fun updateStatus(@Param("id") id: UUID, @Param("status") status: String): Int

    fun findAllByActiveTrue(): List<MerchantProduct>
    fun findAllByMerchantIdAndActiveTrue(merchantId: UUID): List<MerchantProduct>
    fun findAllByMerchantBranchId(merchantBranchId: UUID): List<MerchantProduct>
    fun findAllByActiveTrueAndMerchantBranchId(merchantBranchId: UUID): List<MerchantProduct>
    fun findAllByMerchantBranchIdAndProductCategoryId(
        merchantBranchId: UUID,
        productCategoryId: UUID
    ): List<MerchantProduct>
    fun findAllByActiveTrueAndMerchantBranchIdAndProductCategoryId(
        merchantBranchId: UUID,
        productCategoryId: UUID
    ): List<MerchantProduct>

    fun findByMerchantBranchIdAndProductCode(merchantBranchId: UUID, productCode: Long): MerchantProduct?

    @Query("select max(p.productCode) from MerchantProduct p where p.merchantBranchId = :merchantBranchId")
    fun findMaxProductCodeByMerchantBranchId(@Param("merchantBranchId") merchantBranchId: UUID): Long?

    fun findAllByProductCategoryId(productCategoryId: UUID): List<MerchantProduct>
    fun countByProductCategoryIdAndActiveTrue(productCategoryId: UUID): Long
    fun findByIdAndActiveTrue(id: UUID): Optional<MerchantProduct>
    fun findByIdAndActiveTrueAndMerchantBranchId(id: UUID, merchantBranchId: UUID): Optional<MerchantProduct>
    fun findByIdAndMerchantBranchId(id: UUID, merchantBranchId: UUID): Optional<MerchantProduct>
}

interface CatalogCategoryCount {
    val categoryId: UUID?
    val productCount: Long
}
