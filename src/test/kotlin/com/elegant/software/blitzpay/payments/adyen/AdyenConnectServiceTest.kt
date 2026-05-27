package com.elegant.software.blitzpay.payments.adyen

import com.adyen.model.balanceplatform.AccountHolder
import com.adyen.model.balanceplatform.BalanceAccount
import com.adyen.model.legalentitymanagement.LegalEntity
import com.adyen.model.legalentitymanagement.OnboardingLink
import com.adyen.service.balanceplatform.AccountHoldersApi
import com.adyen.service.balanceplatform.BalanceAccountsApi
import com.adyen.service.legalentitymanagement.HostedOnboardingApi
import com.adyen.service.legalentitymanagement.LegalEntitiesApi
import com.elegant.software.blitzpay.merchant.api.AdyenConnectGateway
import com.elegant.software.blitzpay.merchant.api.MerchantDetailsResponse
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.MerchantOperationalStatus
import com.elegant.software.blitzpay.payments.adyen.service.AdyenConnectService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class AdyenConnectServiceTest {

    private val adyenConnectGateway: AdyenConnectGateway = mock()
    private val legalEntitiesApi: LegalEntitiesApi = mock()
    private val hostedOnboardingApi: HostedOnboardingApi = mock()
    private val accountHoldersApi: AccountHoldersApi = mock()
    private val balanceAccountsApi: BalanceAccountsApi = mock()

    private val service = AdyenConnectService(
        adyenConnectGateway = adyenConnectGateway,
        legalEntitiesApi = legalEntitiesApi,
        hostedOnboardingApi = hostedOnboardingApi,
        accountHoldersApi = accountHoldersApi,
        balanceAccountsApi = balanceAccountsApi,
    )

    private val merchantId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val returnUrl = "https://admin.blitzpay.local/return"
    private val redirectUrl = "https://admin.blitzpay.local/redirect"

    private fun merchantDetails() = MerchantDetailsResponse(
        applicationId = merchantId,
        applicationReference = "REF-001",
        merchantName = "Test Merchant",
        registrationNumber = "12345678",
        businessType = "RESTAURANT",
        operatingCountry = "DE",
        legalBusinessName = "Test Merchant GmbH",
        primaryBusinessAddress = "1 Test Street, Berlin",
        contactFullName = "Jane Doe",
        contactEmail = "jane@testmerchant.com",
        contactPhoneNumber = "+4912345678",
        status = MerchantOnboardingStatus.ACTIVE,
        submittedAt = null,
        lastUpdatedAt = Instant.EPOCH,
    )

    @Test
    fun `createOnboardingUrl creates new accounts on first call and returns onboarding URL`() {
        val legalEntityId = "LE-001"
        val accountHolderId = "AH-001"
        val balanceAccountId = "BA-001"
        val expectedUrl = "https://kyc.adyen.com/onboarding/test"

        whenever(adyenConnectGateway.findLegalEntityId(merchantId)).thenReturn(null)
        whenever(adyenConnectGateway.getDetails(merchantId)).thenReturn(merchantDetails())

        val legalEntity = LegalEntity().also { it.id = legalEntityId }
        val accountHolder = AccountHolder().also { it.id = accountHolderId }
        val balanceAccount = BalanceAccount().also { it.id = balanceAccountId }
        val onboardingLink = OnboardingLink().also { it.url = expectedUrl }

        whenever(legalEntitiesApi.createLegalEntity(any())).thenReturn(legalEntity)
        whenever(accountHoldersApi.createAccountHolder(any())).thenReturn(accountHolder)
        whenever(balanceAccountsApi.createBalanceAccount(any())).thenReturn(balanceAccount)
        whenever(hostedOnboardingApi.getLinkToAdyenhostedOnboardingPage(any(), any())).thenReturn(onboardingLink)

        val result = service.createOnboardingUrl(merchantId, returnUrl, redirectUrl).block()

        assertEquals(expectedUrl, result)
        verify(adyenConnectGateway).save(merchantId, legalEntityId, accountHolderId, balanceAccountId)
        verify(legalEntitiesApi).createLegalEntity(any())
        verify(accountHoldersApi).createAccountHolder(any())
        verify(balanceAccountsApi).createBalanceAccount(any())
    }

    @Test
    fun `createOnboardingUrl reuses existing accounts on subsequent call`() {
        val existingLegalEntityId = "LE-EXISTING"
        val expectedUrl = "https://kyc.adyen.com/onboarding/refresh"
        val onboardingLink = OnboardingLink().also { it.url = expectedUrl }

        whenever(adyenConnectGateway.findLegalEntityId(merchantId)).thenReturn(existingLegalEntityId)
        whenever(hostedOnboardingApi.getLinkToAdyenhostedOnboardingPage(any(), any())).thenReturn(onboardingLink)

        val result = service.createOnboardingUrl(merchantId, returnUrl, redirectUrl).block()

        assertEquals(expectedUrl, result)
        // Should NOT create new entities
        verify(legalEntitiesApi, never()).createLegalEntity(any())
        verify(accountHoldersApi, never()).createAccountHolder(any())
        verify(balanceAccountsApi, never()).createBalanceAccount(any())
        verify(adyenConnectGateway, never()).save(any(), any(), any(), any())
    }

    @Test
    fun `createRefreshUrl returns onboarding URL for existing merchant`() {
        val existingLegalEntityId = "LE-EXISTING"
        val expectedUrl = "https://kyc.adyen.com/onboarding/refresh-link"
        val onboardingLink = OnboardingLink().also { it.url = expectedUrl }

        whenever(adyenConnectGateway.findLegalEntityId(merchantId)).thenReturn(existingLegalEntityId)
        whenever(hostedOnboardingApi.getLinkToAdyenhostedOnboardingPage(any(), any())).thenReturn(onboardingLink)

        val result = service.createRefreshUrl(merchantId, returnUrl).block()

        assertEquals(expectedUrl, result)
    }

    @Test
    fun `createRefreshUrl throws NoSuchElementException when merchant not onboarded`() {
        whenever(adyenConnectGateway.findLegalEntityId(merchantId)).thenReturn(null)

        assertThrows(NoSuchElementException::class.java) {
            service.createRefreshUrl(merchantId, returnUrl).block()
        }
    }

    @Test
    fun `findLegalEntityId delegates to gateway`() {
        whenever(adyenConnectGateway.findLegalEntityId(merchantId)).thenReturn("LE-123")
        assertEquals("LE-123", service.findLegalEntityId(merchantId))
    }

    @Test
    fun `findBalanceAccountId delegates to gateway`() {
        whenever(adyenConnectGateway.findBalanceAccountId(merchantId)).thenReturn("BA-123")
        assertEquals("BA-123", service.findBalanceAccountId(merchantId))
    }

    @Test
    fun `isReceivePaymentsEnabled delegates to gateway`() {
        whenever(adyenConnectGateway.isReceivePaymentsEnabled(merchantId)).thenReturn(true)
        assertEquals(true, service.isReceivePaymentsEnabled(merchantId))
    }
}
