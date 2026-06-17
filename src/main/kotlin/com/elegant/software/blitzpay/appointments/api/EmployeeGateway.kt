package com.elegant.software.blitzpay.appointments.api

import java.util.UUID

interface EmployeeGateway {
    fun lockEmployee(employeeId: UUID)
    fun findNameById(employeeId: UUID): String?
}
