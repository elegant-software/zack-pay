package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MerchantActivated
import com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest
import com.elegant.software.blitzpay.merchant.iam.service.MerchantGroupSyncService
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantMobility
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

@Service
@Transactional
class MerchantRegistrationService(
    private val merchantRepository: MerchantRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val merchantVerticalService: MerchantVerticalService,
    private val groupSyncService: Optional<MerchantGroupSyncService>,
) {

    private val log = LoggerFactory.getLogger(MerchantRegistrationService::class.java)

    fun register(request: RegisterMerchantRequest): Merchant {
        merchantVerticalService.validate(request.businessType)

        val merchant = Merchant(
            merchantName = request.merchantName,
            businessType = request.businessType,
            operatingCountry = request.operatingCountry,
            merchantMobility = request.mobility ?: MerchantMobility.BRICK_AND_MORTAR,
        )

        val saved = merchantRepository.save(merchant)
        log.info("Merchant registered: id={} name={}", saved.id, saved.merchantName)

        groupSyncService.ifPresent { it.ensureMerchantGroupExists(saved.id, saved.merchantName) }
        eventPublisher.publishEvent(MerchantActivated(saved.id, saved.merchantName))

        return saved
    }

    @Transactional(readOnly = true)
    fun findById(merchantId: UUID): Merchant =
        merchantRepository.findById(merchantId)
            .orElseThrow { NoSuchElementException("Merchant not found: $merchantId") }

    @Transactional(readOnly = true)
    fun findByName(name: String): Merchant? =
        merchantRepository.findByMerchantName(name)
}
