package com.elegant.software.blitzpay.appointments.api

import java.util.UUID

interface AppointmentQueryService {
    fun hasFutureBookedForEmployee(employeeId: UUID): Boolean
    fun hasFutureBookedForBranch(branchId: UUID): Boolean
    fun hasFutureBookedForMerchant(merchantId: UUID): Boolean
    fun hasFutureBookedForProduct(productId: UUID): Boolean
    /** Caller resolves productIds for the category to avoid reverse coupling into merchant data. */
    fun hasFutureBookedForProducts(productIds: Set<UUID>): Boolean
}
