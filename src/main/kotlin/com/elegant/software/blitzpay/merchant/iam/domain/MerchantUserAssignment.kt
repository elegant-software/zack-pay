package com.elegant.software.blitzpay.merchant.iam.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "merchant_user_assignments", schema = "blitzpay")
class MerchantUserAssignment(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "keycloak_user_id", nullable = false, updatable = false)
    val keycloakUserId: UUID,

    @Column(name = "merchant_application_id", nullable = false, updatable = false)
    val merchantApplicationId: UUID,

    @Column(name = "branch_id")
    val branchId: UUID? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    val role: MerchantOperationsRole,

    @Column(name = "employee_id")
    val employeeId: UUID? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
