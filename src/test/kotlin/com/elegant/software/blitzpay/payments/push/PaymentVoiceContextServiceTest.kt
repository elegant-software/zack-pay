package com.elegant.software.blitzpay.payments.push

import com.elegant.software.blitzpay.order.api.PaymentStatusCode
import com.elegant.software.blitzpay.payments.push.api.RecentPaymentSummary
import com.elegant.software.blitzpay.payments.push.service.PaymentStatusService
import com.elegant.software.blitzpay.payments.push.service.PaymentVoiceContextService
import com.elegant.software.blitzpay.payments.push.persistence.model.DeviceRegistrationRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class PaymentVoiceContextServiceTest {
    private val paymentStatusService = mock<PaymentStatusService>()
    private val deviceRegistrationRepository = mock<DeviceRegistrationRepository>()
    private val service = PaymentVoiceContextService(paymentStatusService, deviceRegistrationRepository)

    @Test
    fun `delegates recent payment lookup to payment status service`() {
        val expected = listOf(
            RecentPaymentSummary(
                paymentRequestId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                status = PaymentStatusCode.SETTLED,
                updatedAt = Instant.parse("2026-04-25T10:15:30Z"),
                orderId = "ORD-42",
                amountMinorUnits = 1299,
                currency = "EUR",
            )
        )
        whenever(paymentStatusService.findRecentBySubject("user-123", 3)).thenReturn(expected)

        val result = service.findRecentPaymentsBySubject("user-123", 3)

        assertEquals(expected, result)
    }

    @Test
    fun `checks push registrations when initializing payment status for an order`() {
        val paymentRequestId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        whenever(deviceRegistrationRepository.countByOrderIdAndInvalidFalse("ORD-42")).thenReturn(0)

        service.initialize(
            paymentRequestId = paymentRequestId,
            payerRef = null,
            orderId = "ORD-42",
            amountMinorUnits = 700,
            currency = "EUR",
        )

        verify(paymentStatusService).initialize(
            paymentRequestId = paymentRequestId,
            payerRef = null,
            orderId = "ORD-42",
            amountMinorUnits = 700,
            currency = "EUR",
        )
        verify(deviceRegistrationRepository).countByOrderIdAndInvalidFalse("ORD-42")
    }
}
