package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MerchantVerticalResponse
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantVerticalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class MerchantVerticalService(
    private val repository: MerchantVerticalRepository,
) {
    fun listActive(): List<MerchantVerticalResponse> =
        repository.findAllByActiveTrue()
            .map { MerchantVerticalResponse(code = it.code, displayName = it.displayName) }

    fun validate(code: String) {
        require(repository.existsByCodeAndActiveTrue(code)) {
            "businessType '$code' is not a recognised merchant vertical"
        }
    }
}
