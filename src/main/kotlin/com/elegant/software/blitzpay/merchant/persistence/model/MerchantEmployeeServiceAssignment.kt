package com.elegant.software.blitzpay.merchant.persistence.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchant_employee_service_assignments", schema = "blitzpay")
class MerchantEmployeeServiceAssignment(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "employee_id", nullable = false, updatable = false)
    val employeeId: UUID,

    @Column(name = "product_category_id", nullable = false, updatable = false)
    val productCategoryId: UUID,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
