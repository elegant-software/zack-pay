package com.elegant.software.blitzpay.appointments.service

import com.elegant.software.blitzpay.appointments.api.AppointmentQueryService
import com.elegant.software.blitzpay.appointments.persistence.AppointmentRepository
import com.elegant.software.blitzpay.appointments.persistence.AppointmentStatus
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class AppointmentQueryServiceImpl(
    private val appointmentRepo: AppointmentRepository,
) : AppointmentQueryService {

    override fun hasFutureBookedForEmployee(employeeId: UUID): Boolean =
        appointmentRepo.existsByEmployeeIdAndStatusAndStartTimeAfter(employeeId, AppointmentStatus.BOOKED, Instant.now())

    override fun hasFutureBookedForBranch(branchId: UUID): Boolean =
        appointmentRepo.existsByBranchIdAndStatusAndStartTimeAfter(branchId, AppointmentStatus.BOOKED, Instant.now())

    override fun hasFutureBookedForMerchant(merchantId: UUID): Boolean =
        appointmentRepo.existsByMerchantIdAndStatusAndStartTimeAfter(merchantId, AppointmentStatus.BOOKED, Instant.now())

    override fun hasFutureBookedForProduct(productId: UUID): Boolean =
        appointmentRepo.existsByProductIdAndStatusAndStartTimeAfter(productId, AppointmentStatus.BOOKED, Instant.now())

    override fun hasFutureBookedForProducts(productIds: Set<UUID>): Boolean {
        if (productIds.isEmpty()) return false
        return appointmentRepo.existsByProductIdInAndStatusBookedAndStartTimeAfter(productIds, Instant.now())
    }
}
