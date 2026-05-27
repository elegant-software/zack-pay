package com.elegant.software.blitzpay.payments.adyen.service

import com.adyen.model.balanceplatform.AccountHolderInfo
import com.adyen.model.balanceplatform.BalanceAccountInfo
import com.adyen.model.legalentitymanagement.LegalEntityInfoRequiredType
import com.adyen.model.legalentitymanagement.OnboardingLinkInfo
import com.adyen.service.balanceplatform.AccountHoldersApi
import com.adyen.service.balanceplatform.BalanceAccountsApi
import com.adyen.service.legalentitymanagement.HostedOnboardingApi
import com.adyen.service.legalentitymanagement.LegalEntitiesApi
import com.elegant.software.blitzpay.merchant.api.AdyenConnectGateway
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.UUID

@Service
@Profile("!contract-test")
class AdyenConnectService(
    private val adyenConnectGateway: AdyenConnectGateway,
    private val legalEntitiesApi: LegalEntitiesApi,
    private val hostedOnboardingApi: HostedOnboardingApi,
    private val accountHoldersApi: AccountHoldersApi,
    private val balanceAccountsApi: BalanceAccountsApi,
) {
    private val log = LoggerFactory.getLogger(AdyenConnectService::class.java)

    /**
     * Creates a new Adyen LegalEntity, AccountHolder, and BalanceAccount if none exist for the
     * merchant, then generates an onboarding link via the Adyen Hosted Onboarding API.
     *
     * @param merchantId BlitzPay merchant UUID
     * @param returnUrl  URL where the admin is returned after the merchant completes onboarding
     * @param redirectUrl Adyen redirectUrl — the final landing page after Adyen's onboarding flow
     */
    fun createOnboardingUrl(merchantId: UUID, returnUrl: String, redirectUrl: String): Mono<String> =
        Mono.fromCallable {
            val existingLegalEntityId = adyenConnectGateway.findLegalEntityId(merchantId)
            val legalEntityId = existingLegalEntityId ?: run {
                val details = adyenConnectGateway.getDetails(merchantId)

                // 1. Create LegalEntity (organization shell — KYC details filled by merchant)
                val legalEntityRequest = LegalEntityInfoRequiredType()
                    .type(LegalEntityInfoRequiredType.TypeEnum.ORGANIZATION)
                    .reference(merchantId.toString())
                val legalEntity = legalEntitiesApi.createLegalEntity(legalEntityRequest)
                log.info(
                    "adyen_connect legal entity created merchantId={} legalEntityId={}",
                    merchantId, legalEntity.id,
                )

                // 2. Create AccountHolder linked to the LegalEntity
                val accountHolderInfo = AccountHolderInfo()
                    .legalEntityId(legalEntity.id)
                    .description(details.merchantName.takeIf { it.isNotBlank() } ?: details.legalBusinessName)
                    .reference(merchantId.toString())
                val accountHolder = accountHoldersApi.createAccountHolder(accountHolderInfo)
                log.info(
                    "adyen_connect account holder created merchantId={} accountHolderId={}",
                    merchantId, accountHolder.id,
                )

                // 3. Create BalanceAccount linked to the AccountHolder
                val balanceAccountInfo = BalanceAccountInfo()
                    .accountHolderId(accountHolder.id)
                    .description("BlitzPay balance account for merchant $merchantId")
                    .reference(merchantId.toString())
                val balanceAccount = balanceAccountsApi.createBalanceAccount(balanceAccountInfo)
                log.info(
                    "adyen_connect balance account created merchantId={} balanceAccountId={}",
                    merchantId, balanceAccount.id,
                )

                // 4. Persist identifiers
                adyenConnectGateway.save(merchantId, legalEntity.id, accountHolder.id, balanceAccount.id)
                legalEntity.id
            }

            // 5. Generate onboarding link
            val onboardingLinkInfo = OnboardingLinkInfo().redirectUrl(redirectUrl)
            val onboardingLink = hostedOnboardingApi.getLinkToAdyenhostedOnboardingPage(
                legalEntityId,
                onboardingLinkInfo,
            )
            log.info(
                "adyen_connect onboarding link created merchantId={} legalEntityId={}",
                merchantId, legalEntityId,
            )
            onboardingLink.url
        }.subscribeOn(Schedulers.boundedElastic())

    /**
     * Regenerates an Adyen onboarding link for a merchant who has already been provisioned
     * (legalEntityId already exists).
     */
    fun createRefreshUrl(merchantId: UUID, returnUrl: String): Mono<String> =
        Mono.fromCallable {
            val legalEntityId = adyenConnectGateway.findLegalEntityId(merchantId)
                ?: throw NoSuchElementException("No Adyen account found for merchant $merchantId")

            val onboardingLinkInfo = OnboardingLinkInfo().redirectUrl(returnUrl)
            val onboardingLink = hostedOnboardingApi.getLinkToAdyenhostedOnboardingPage(
                legalEntityId,
                onboardingLinkInfo,
            )
            log.info(
                "adyen_connect refresh link created merchantId={} legalEntityId={}",
                merchantId, legalEntityId,
            )
            onboardingLink.url
        }.subscribeOn(Schedulers.boundedElastic())

    fun findLegalEntityId(merchantId: UUID): String? =
        adyenConnectGateway.findLegalEntityId(merchantId)

    fun findBalanceAccountId(merchantId: UUID): String? =
        adyenConnectGateway.findBalanceAccountId(merchantId)

    fun isReceivePaymentsEnabled(merchantId: UUID): Boolean =
        adyenConnectGateway.isReceivePaymentsEnabled(merchantId)
}
