package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MerchantRepository : JpaRepository<Merchant, UUID>, JpaSpecificationExecutor<Merchant> {
    fun findByMerchantName(merchantName: String): Merchant?

    @Query(
        value = """
            SELECT m.*,
                   (6371000 * acos(
                       cos(radians(:lat)) * cos(radians(m.latitude))
                       * cos(radians(m.longitude) - radians(:lng))
                       + sin(radians(:lat)) * sin(radians(m.latitude))
                   )) AS distance_meters
            FROM blitzpay.merchants m
            WHERE m.latitude IS NOT NULL
              AND m.longitude IS NOT NULL
              AND (6371000 * acos(
                       cos(radians(:lat)) * cos(radians(m.latitude))
                       * cos(radians(m.longitude) - radians(:lng))
                       + sin(radians(:lat)) * sin(radians(m.latitude))
                   )) <= :radiusMeters
            ORDER BY distance_meters
        """,
        nativeQuery = true
    )
    fun findNearby(
        @Param("lat") lat: Double,
        @Param("lng") lng: Double,
        @Param("radiusMeters") radiusMeters: Double
    ): List<Merchant>

    @Query(
        value = """
            SELECT *
            FROM blitzpay.merchants
            WHERE google_place_id IS NOT NULL
              AND (place_enrichment_status IS NULL OR place_enrichment_status IN ('PENDING', 'FAILED'))
            ORDER BY last_updated_at
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findPlaceIdsNeedingEnrichment(@Param("limit") limit: Int): List<Merchant>

    fun findAllByMerchantStatus(merchantStatus: com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus): List<Merchant>
}
