package com.elegant.software.blitzpay.merchant.web

import com.elegant.software.blitzpay.merchant.domain.MerchantEmployee
import com.elegant.software.blitzpay.merchant.domain.MerchantEmployeeBranchAssignment
import com.elegant.software.blitzpay.merchant.domain.MerchantEmployeeServiceAssignment
import com.elegant.software.blitzpay.merchant.service.MerchantEmployeeService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@Tag(name = "Employees", description = "Employee management for appointment booking")
@RequestMapping("/{version:v\\d+(?:\\.\\d+)*}/merchants/{merchantId}/employees", version = "1")
class MerchantEmployeeController(
    private val employeeService: MerchantEmployeeService,
    private val callerResolver: MerchantCallerResolver,
) {

    @GetMapping
    fun list(
        @PathVariable merchantId: UUID,
        @RequestParam(required = false) branchId: UUID?,
        @RequestParam(required = false) productCategoryId: UUID?,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<List<EmployeeResponse>> {
        callerResolver.resolve(auth, merchantId).requireManagerOrEmployee(merchantId)
        val employees = if (branchId != null && productCategoryId != null) {
            employeeService.listEligibleForServiceAtBranch(branchId, productCategoryId)
        } else {
            employeeService.listByMerchant(merchantId)
        }
        return ResponseEntity.ok(employees.map { it.toResponse() })
    }

    @PostMapping
    fun create(
        @PathVariable merchantId: UUID,
        @Valid @RequestBody req: CreateEmployeeRequest,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<EmployeeResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        val employee = employeeService.create(merchantId, req.name!!)
        return ResponseEntity.status(HttpStatus.CREATED).body(employee.toResponse())
    }

    @PutMapping("/{employeeId}/name")
    fun rename(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @Valid @RequestBody req: RenameEmployeeRequest,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<EmployeeResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        return ResponseEntity.ok(employeeService.rename(employeeId, req.name!!).toResponse())
    }

    @PutMapping("/{employeeId}/activate")
    fun activate(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<EmployeeResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        return ResponseEntity.ok(employeeService.activate(employeeId).toResponse())
    }

    @PutMapping("/{employeeId}/deactivate")
    fun deactivate(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<EmployeeResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        return ResponseEntity.ok(employeeService.deactivate(employeeId).toResponse())
    }

    // ── Branch assignments ──────────────────────────────────────────────────

    @GetMapping("/{employeeId}/branch-assignments")
    fun listBranchAssignments(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<List<BranchAssignmentResponse>> {
        callerResolver.resolve(auth, merchantId).requireManagerOrEmployee(merchantId)
        return ResponseEntity.ok(employeeService.listBranchAssignments(employeeId).map { it.toResponse() })
    }

    @PostMapping("/{employeeId}/branch-assignments")
    fun addBranchAssignment(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @Valid @RequestBody req: BranchAssignmentRequest,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<BranchAssignmentResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        val assignment = employeeService.addBranchAssignment(employeeId, req.branchId!!)
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment.toResponse())
    }

    @DeleteMapping("/{employeeId}/branch-assignments/{branchId}")
    fun removeBranchAssignment(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @PathVariable branchId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<Void> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        employeeService.removeBranchAssignment(employeeId, branchId)
        return ResponseEntity.noContent().build()
    }

    // ── Service assignments ─────────────────────────────────────────────────

    @GetMapping("/{employeeId}/service-assignments")
    fun listServiceAssignments(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<List<ServiceAssignmentResponse>> {
        callerResolver.resolve(auth, merchantId).requireManagerOrEmployee(merchantId)
        val assignments = employeeService.listServiceAssignments(employeeId)
        val categoryNames = employeeService.listServiceAssignmentCategoryNames(employeeId)
        return ResponseEntity.ok(assignments.map { it.toResponse(categoryNames[it.productCategoryId]) })
    }

    @PostMapping("/{employeeId}/service-assignments")
    fun addServiceAssignment(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @Valid @RequestBody req: ServiceAssignmentRequest,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<ServiceAssignmentResponse> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        val assignment = employeeService.addServiceAssignment(employeeId, req.productCategoryId!!)
        return ResponseEntity.status(HttpStatus.CREATED).body(assignment.toResponse())
    }

    @DeleteMapping("/{employeeId}/service-assignments/{productCategoryId}")
    fun removeServiceAssignment(
        @PathVariable merchantId: UUID,
        @PathVariable employeeId: UUID,
        @PathVariable productCategoryId: UUID,
        @RequestHeader("Authorization") auth: String,
    ): ResponseEntity<Void> {
        callerResolver.resolve(auth, merchantId).requireManager(merchantId)
        employeeService.removeServiceAssignment(employeeId, productCategoryId)
        return ResponseEntity.noContent().build()
    }
}

// ── DTOs ────────────────────────────────────────────────────────────────────

data class CreateEmployeeRequest(@field:NotBlank val name: String?)
data class RenameEmployeeRequest(@field:NotBlank val name: String?)
data class BranchAssignmentRequest(@field:NotNull val branchId: UUID?)
data class ServiceAssignmentRequest(@field:NotNull val productCategoryId: UUID?)

data class EmployeeResponse(
    val id: UUID,
    val merchantId: UUID,
    val name: String,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class BranchAssignmentResponse(val id: UUID, val employeeId: UUID, val branchId: UUID, val createdAt: Instant)
data class ServiceAssignmentResponse(val id: UUID, val employeeId: UUID, val productCategoryId: UUID, val categoryName: String?, val createdAt: Instant)

fun MerchantEmployee.toResponse() = EmployeeResponse(id, merchantId, name, status, createdAt, updatedAt)
fun MerchantEmployeeBranchAssignment.toResponse() = BranchAssignmentResponse(id, employeeId, branchId, createdAt)
fun MerchantEmployeeServiceAssignment.toResponse(categoryName: String? = null) = ServiceAssignmentResponse(id, employeeId, productCategoryId, categoryName, createdAt)
