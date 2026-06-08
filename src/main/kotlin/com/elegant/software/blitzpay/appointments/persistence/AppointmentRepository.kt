package com.elegant.software.blitzpay.appointments.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.time.Instant
import java.util.UUID

interface AppointmentRepository : JpaRepository<AppointmentEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM AppointmentEntity e WHERE e.id = :id")
    fun findByIdForUpdate(id: UUID): AppointmentEntity?

    fun findAllByMerchantIdAndStartTimeBetween(
        merchantId: UUID, from: Instant, to: Instant,
    ): List<AppointmentEntity>

    fun findAllByBranchIdAndStartTimeBetween(
        branchId: UUID, from: Instant, to: Instant,
    ): List<AppointmentEntity>

    fun findAllByEmployeeIdAndStatusAndStartTimeBetween(
        employeeId: UUID, status: String, from: Instant, to: Instant,
    ): List<AppointmentEntity>

    fun findAllByShopperAccountId(shopperAccountId: String): List<AppointmentEntity>

    @Query("""
        SELECT a FROM AppointmentEntity a
        WHERE a.employeeId = :employeeId
          AND a.status = 'BOOKED'
          AND a.startTime < :endTime
          AND a.endTime > :startTime
    """)
    fun findOverlappingBooked(
        @Param("employeeId") employeeId: UUID,
        @Param("startTime") startTime: Instant,
        @Param("endTime") endTime: Instant,
    ): List<AppointmentEntity>

    @Query("""
        SELECT a FROM AppointmentEntity a
        WHERE a.shopperAccountId = :shopperAccountId
          AND a.status = 'BOOKED'
          AND a.startTime < :endTime
          AND a.endTime > :startTime
    """)
    fun findShopperOverlappingBooked(
        @Param("shopperAccountId") shopperAccountId: String,
        @Param("startTime") startTime: Instant,
        @Param("endTime") endTime: Instant,
    ): List<AppointmentEntity>

    @Query("""
        SELECT a FROM AppointmentEntity a
        WHERE a.reminderSent = FALSE
          AND a.status = 'BOOKED'
          AND a.shopperAccountId IS NOT NULL
          AND a.reminderDueAt IS NOT NULL
          AND a.reminderDueAt <= :now
    """)
    fun findDueReminders(@Param("now") now: Instant): List<AppointmentEntity>

    fun existsByEmployeeIdAndStatusAndStartTimeAfter(
        employeeId: UUID, status: String, cutoff: Instant,
    ): Boolean

    fun existsByBranchIdAndStatusAndStartTimeAfter(
        branchId: UUID, status: String, cutoff: Instant,
    ): Boolean

    fun existsByMerchantIdAndStatusAndStartTimeAfter(
        merchantId: UUID, status: String, cutoff: Instant,
    ): Boolean

    fun existsByProductIdAndStatusAndStartTimeAfter(
        productId: UUID, status: String, cutoff: Instant,
    ): Boolean

    @Query("""
        SELECT CASE WHEN COUNT(a) > 0 THEN TRUE ELSE FALSE END
        FROM AppointmentEntity a
        WHERE a.productId IN :productIds
          AND a.status = 'BOOKED'
          AND a.startTime > :cutoff
    """)
    fun existsByProductIdInAndStatusBookedAndStartTimeAfter(
        @Param("productIds") productIds: Set<UUID>,
        @Param("cutoff") cutoff: Instant,
    ): Boolean

    fun existsByEmployeeIdAndBranchIdAndStatusAndStartTimeAfter(
        employeeId: UUID, branchId: UUID, status: String, cutoff: Instant,
    ): Boolean
}
