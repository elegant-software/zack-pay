package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.appointments.api.EmployeeGateway
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantEmployeeRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Component
@Transactional(readOnly = true)
class EmployeeGatewayImpl(
    private val employeeRepo: MerchantEmployeeRepository,
) : EmployeeGateway {

    @Transactional
    override fun lockEmployee(employeeId: UUID) {
        employeeRepo.findByIdForUpdate(employeeId)
            ?: throw NoSuchElementException("Employee $employeeId not found")
    }

    override fun findNameById(employeeId: UUID): String? =
        employeeRepo.findById(employeeId).map { it.name }.orElse(null)
}
