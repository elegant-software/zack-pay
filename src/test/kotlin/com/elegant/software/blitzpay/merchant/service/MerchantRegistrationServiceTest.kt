package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MerchantActivated
import com.elegant.software.blitzpay.merchant.api.RegisterMerchantRequest
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MerchantRegistrationServiceTest {

    private val repository = mock<MerchantRepository>()
    private val eventPublisher = mock<ApplicationEventPublisher>()
    private val merchantVerticalService = mock<MerchantVerticalService>()
    private val service = MerchantRegistrationService(repository, eventPublisher, merchantVerticalService, Optional.empty())

    private val validRequest = RegisterMerchantRequest(
        merchantName = "Acme GmbH",
        businessType = "RETAIL",
        operatingCountry = "DE",
    )

    @Test
    fun `register creates a merchant and saves it`() {
        whenever(merchantVerticalService.validate(any())).thenAnswer { }
        whenever(repository.save(any<Merchant>())).thenAnswer { it.arguments[0] }

        val result = service.register(validRequest)

        assertEquals("Acme GmbH", result.merchantName)
        assertEquals("RETAIL", result.businessType)
        assertEquals("DE", result.operatingCountry)
    }

    @Test
    fun `register publishes MerchantActivated event`() {
        whenever(merchantVerticalService.validate(any())).thenAnswer { }
        whenever(repository.save(any<Merchant>())).thenAnswer { it.arguments[0] }

        service.register(validRequest)

        val captor = argumentCaptor<MerchantActivated>()
        verify(eventPublisher).publishEvent(captor.capture())
        assertEquals("Acme GmbH", captor.firstValue.merchantName)
    }

    @Test
    fun `findById throws when merchant does not exist`() {
        val unknownId = UUID.randomUUID()
        whenever(repository.findById(unknownId)).thenReturn(Optional.empty())

        assertFailsWith<NoSuchElementException> {
            service.findById(unknownId)
        }
    }

    @Test
    fun `findById returns merchant when it exists`() {
        val merchant = Merchant(merchantName = "Acme GmbH", businessType = "RETAIL", operatingCountry = "DE")
        whenever(repository.findById(merchant.id)).thenReturn(Optional.of(merchant))

        val result = service.findById(merchant.id)

        assertEquals(merchant.id, result.id)
    }
}
