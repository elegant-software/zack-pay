package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.AdyenConnectGateway
import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.domain.MerchantProviderAccount
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantAdyenProviderAccount
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantAdyenProviderAccountRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProviderAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
@Profile("!contract-test")
class AdyenConnectGatewayImpl(
    private val providerAccountRepository: MerchantProviderAccountRepository,
    private val adyenProviderAccountRepository: MerchantAdyenProviderAccountRepository,
    private val managementService: MerchantManagementService,
) : AdyenConnectGateway {

    private val log = LoggerFactory.getLogger(AdyenConnectGatewayImpl::class.java)

    override fun save(merchantId: UUID, legalEntityId: String, accountHolderId: String, balanceAccountId: String) {
        val providerAccount = providerAccountRepository
            .findByMerchantApplicationIdAndPspCode(merchantId, "ADYEN")
            ?: providerAccountRepository.save(
                MerchantProviderAccount(
                    merchantApplicationId = merchantId,
                    pspCode = "ADYEN",
                    enabled = true,
                )
            )

        if (adyenProviderAccountRepository.existsById(providerAccount.id)) {
            log.warn(
                "adyen_connect save skipped — record already exists merchantId={} legalEntityId={}",
                merchantId, legalEntityId,
            )
            return
        }
        adyenProviderAccountRepository.save(
            MerchantAdyenProviderAccount(
                merchantProviderAccountId = providerAccount.id,
                legalEntityId = legalEntityId,
                accountHolderId = accountHolderId,
                balanceAccountId = balanceAccountId,
            )
        )
        log.info(
            "adyen_connect saved merchantId={} legalEntityId={} accountHolderId={} balanceAccountId={}",
            merchantId, legalEntityId, accountHolderId, balanceAccountId,
        )
    }

    override fun onAccountUpdated(
        balanceAccountId: String,
        receivePaymentsEnabled: Boolean,
        detailsSubmitted: Boolean,
    ) {
        val record = adyenProviderAccountRepository.findByBalanceAccountId(balanceAccountId) ?: run {
            log.warn(
                "adyen_connect balanceAccount.updated ignored — no record found for balanceAccountId={}",
                balanceAccountId,
            )
            return
        }
        record.receivePaymentsEnabled = receivePaymentsEnabled
        record.detailsSubmitted = detailsSubmitted
        record.updatedAt = Instant.now()
        adyenProviderAccountRepository.save(record)
        log.info(
            "adyen_connect balanceAccount.updated balanceAccountId={} receivePaymentsEnabled={} detailsSubmitted={}",
            balanceAccountId, receivePaymentsEnabled, detailsSubmitted,
        )
    }

    override fun findLegalEntityId(merchantId: UUID): String? =
        findAdyenProviderAccount(merchantId)?.legalEntityId

    override fun findBalanceAccountId(merchantId: UUID): String? =
        findAdyenProviderAccount(merchantId)?.balanceAccountId

    override fun isReceivePaymentsEnabled(merchantId: UUID): Boolean =
        findAdyenProviderAccount(merchantId)?.receivePaymentsEnabled ?: false

    override fun getDetails(merchantId: UUID): MerchantDetailsResponse =
        managementService.get(merchantId)

    private fun findAdyenProviderAccount(merchantId: UUID): MerchantAdyenProviderAccount? {
        val providerAccount = providerAccountRepository
            .findByMerchantApplicationIdAndPspCode(merchantId, "ADYEN") ?: return null
        return adyenProviderAccountRepository.findById(providerAccount.id).orElse(null)
    }
}
