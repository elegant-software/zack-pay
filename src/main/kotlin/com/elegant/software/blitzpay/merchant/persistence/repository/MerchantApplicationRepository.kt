package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantApplication
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOnboardingStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface MerchantApplicationRepository : JpaRepository<MerchantApplication, UUID>, JpaSpecificationExecutor<MerchantApplication> {
    fun findByBusinessProfileLegalBusinessName(legalBusinessName: String): MerchantApplication?

    fun findByApplicationReference(applicationReference: String): MerchantApplication?

    fun findAllByStatus(status: MerchantOnboardingStatus): List<MerchantApplication>


    fun existsByBusinessProfileRegistrationNumberAndStatusIn(
        registrationNumber: String,
        statuses: Collection<MerchantOnboardingStatus>
    ): Boolean

    @Query(
        value = """
            SELECT m.*,
                   (6371000 * acos(
                       cos(radians(:lat)) * cos(radians(m.latitude))
                       * cos(radians(m.longitude) - radians(:lng))
                       + sin(radians(:lat)) * sin(radians(m.latitude))
                   )) AS distance_meters
            FROM blitzpay.merchant_applications m
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
    ): List<MerchantApplication>

    @Query(
        value = """
            SELECT *
            FROM blitzpay.merchant_applications
            WHERE google_place_id IS NOT NULL
              AND (place_enrichment_status IS NULL OR place_enrichment_status IN ('PENDING', 'FAILED'))
            ORDER BY last_updated_at
            LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findPlaceIdsNeedingEnrichment(@Param("limit") limit: Int): List<MerchantApplication>
}
