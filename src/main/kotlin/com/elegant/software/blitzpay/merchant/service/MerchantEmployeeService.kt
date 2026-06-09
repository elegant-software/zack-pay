package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.appointments.api.AppointmentQueryService
import com.elegant.software.blitzpay.merchant.domain.MerchantEmployee
import com.elegant.software.blitzpay.merchant.domain.MerchantEmployeeBranchAssignment
import com.elegant.software.blitzpay.merchant.domain.MerchantEmployeeServiceAssignment
import com.elegant.software.blitzpay.merchant.domain.MerchantEntityStatus
import com.elegant.software.blitzpay.merchant.persistence.repository.*
import com.elegant.software.blitzpay.merchant.repository.MerchantBranchRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductCategoryRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Service
class MerchantEmployeeService(
    private val employeeRepo: MerchantEmployeeRepository,
    private val branchAssignmentRepo: MerchantEmployeeBranchAssignmentRepository,
    private val serviceAssignmentRepo: MerchantEmployeeServiceAssignmentRepository,
    private val availabilityRepo: MerchantEmployeeAvailabilityRepository,
    private val availabilityExceptionRepo: MerchantEmployeeAvailabilityExceptionRepository,
    private val branchRepo: MerchantBranchRepository,
    private val productRepo: MerchantProductRepository,
    private val productCategoryRepo: MerchantProductCategoryRepository,
    private val appointmentQuery: AppointmentQueryService,
) {
    private val log = LoggerFactory.getLogger(MerchantEmployeeService::class.java)

    @Transactional(readOnly = true)
    fun listByMerchant(merchantId: UUID): List<MerchantEmployee> =
        employeeRepo.findAllByMerchantId(merchantId)

    @Transactional
    fun create(merchantId: UUID, name: String): MerchantEmployee {
        val employee = MerchantEmployee(
            merchantId = merchantId,
            name = name.trim(),
            status = MerchantEntityStatus.INACTIVE,
        )
        return employeeRepo.save(employee).also {
            log.info("Employee {} created for merchant {}", it.id, merchantId)
        }
    }

    @Transactional
    fun rename(employeeId: UUID, newName: String): MerchantEmployee {
        val employee = requireEmployee(employeeId)
        employee.rename(newName)
        return employee
    }

    @Transactional
    fun activate(employeeId: UUID): MerchantEmployee {
        val employee = requireEmployee(employeeId)
        employee.status = MerchantEntityStatus.ACTIVE
        employee.updatedAt = Instant.now()
        return employee
    }

    @Transactional
    fun deactivate(employeeId: UUID): MerchantEmployee {
        val employee = requireEmployee(employeeId)
        check(!appointmentQuery.hasFutureBookedForEmployee(employeeId)) {
            "Employee has future booked appointments — cancel or reschedule them before deactivating"
        }
        employee.deactivate()
        return employee
    }

    // ── Branch assignments ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listBranchAssignments(employeeId: UUID): List<MerchantEmployeeBranchAssignment> =
        branchAssignmentRepo.findAllByEmployeeId(employeeId)

    @Transactional
    fun addBranchAssignment(employeeId: UUID, branchId: UUID): MerchantEmployeeBranchAssignment {
        requireEmployee(employeeId)
        branchRepo.findById(branchId).orElseThrow { NoSuchElementException("Branch $branchId not found") }
        check(!branchAssignmentRepo.existsByEmployeeIdAndBranchId(employeeId, branchId)) {
            "Employee is already assigned to this branch"
        }
        val assignment = MerchantEmployeeBranchAssignment(employeeId = employeeId, branchId = branchId)
        return branchAssignmentRepo.save(assignment)
    }

    @Transactional
    fun removeBranchAssignment(employeeId: UUID, branchId: UUID) {
        val assignment = branchAssignmentRepo.findByEmployeeIdAndBranchId(employeeId, branchId)
            ?: throw NoSuchElementException("No branch assignment found for employee $employeeId at branch $branchId")
        check(!appointmentQuery.hasFutureBookedForEmployee(employeeId)) {
            "Employee has future booked appointments at this branch — cancel or reschedule them first"
        }
        availabilityRepo.deleteAll(availabilityRepo.findAllByEmployeeIdAndBranchId(employeeId, branchId))
        availabilityExceptionRepo.deleteAll(
            availabilityExceptionRepo.findAllByEmployeeIdAndBranchIdAndExceptionDateBetween(
                employeeId, branchId, LocalDate.now(), LocalDate.now().plusYears(10)
            )
        )
        branchAssignmentRepo.delete(assignment)
        log.info("Branch assignment removed: employee={} branch={}", employeeId, branchId)
    }

    // ── Service assignments ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    fun listServiceAssignments(employeeId: UUID): List<MerchantEmployeeServiceAssignment> =
        serviceAssignmentRepo.findAllByEmployeeId(employeeId)

    @Transactional(readOnly = true)
    fun listServiceAssignmentCategoryNames(employeeId: UUID): Map<UUID, String?> {
        val assignments = serviceAssignmentRepo.findAllByEmployeeId(employeeId)
        val categoryIds = assignments.map { it.productCategoryId }.toSet()
        return productCategoryRepo.findAllById(categoryIds).associate { it.id to it.name }
    }

    @Transactional(readOnly = true)
    fun listEligibleForServiceAtBranch(branchId: UUID, productCategoryId: UUID): List<MerchantEmployee> {
        val branchEmployeeIds = branchAssignmentRepo.findAllByBranchId(branchId).map { it.employeeId }.toSet()
        if (branchEmployeeIds.isEmpty()) return emptyList()
        val eligibleIds = serviceAssignmentRepo.findAllByEmployeeIdIn(branchEmployeeIds)
            .filter { it.productCategoryId == productCategoryId }
            .map { it.employeeId }
            .toSet()
        return employeeRepo.findAllById(eligibleIds).filter { it.status == MerchantEntityStatus.ACTIVE }
    }

    @Transactional
    fun addServiceAssignment(employeeId: UUID, productCategoryId: UUID): MerchantEmployeeServiceAssignment {
        requireEmployee(employeeId)
        check(!serviceAssignmentRepo.existsByEmployeeIdAndProductCategoryId(employeeId, productCategoryId)) {
            "Employee already has this service assignment"
        }
        val assignment = MerchantEmployeeServiceAssignment(employeeId = employeeId, productCategoryId = productCategoryId)
        return serviceAssignmentRepo.save(assignment)
    }

    @Transactional
    fun removeServiceAssignment(employeeId: UUID, productCategoryId: UUID) {
        val assignment = serviceAssignmentRepo.findByEmployeeIdAndProductCategoryId(employeeId, productCategoryId)
            ?: throw NoSuchElementException("No service assignment found for employee $employeeId and category $productCategoryId")
        val productIds = productRepo.findAllByProductCategoryId(productCategoryId).map { p -> p.id }.toSet()
        check(!appointmentQuery.hasFutureBookedForProducts(productIds)) {
            "Employee has future booked appointments for products in this category — cancel or reschedule them first"
        }
        serviceAssignmentRepo.delete(assignment)
    }

    private fun requireEmployee(employeeId: UUID): MerchantEmployee =
        employeeRepo.findById(employeeId).orElseThrow { NoSuchElementException("Employee $employeeId not found") }
}
