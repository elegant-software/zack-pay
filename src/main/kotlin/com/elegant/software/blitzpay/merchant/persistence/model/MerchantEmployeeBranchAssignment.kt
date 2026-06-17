package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchant_employee_branch_assignments", schema = "blitzpay")
class MerchantEmployeeBranchAssignment(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "employee_id", nullable = false, updatable = false)
    val employeeId: UUID,

    @Column(name = "branch_id", nullable = false, updatable = false)
    val branchId: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
