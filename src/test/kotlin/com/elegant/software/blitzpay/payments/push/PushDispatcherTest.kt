package com.elegant.software.blitzpay.payments.push

import com.elegant.software.blitzpay.order.api.PaymentStatusChanged
import com.elegant.software.blitzpay.order.api.PaymentStatusCode
import com.elegant.software.blitzpay.payments.push.service.ExpoPushClient
import com.elegant.software.blitzpay.payments.push.service.PushDispatcher
import com.elegant.software.blitzpay.payments.push.persistence.model.DeviceRegistrationEntity
import com.elegant.software.blitzpay.payments.push.persistence.model.DeviceRegistrationRepository
import com.elegant.software.blitzpay.payments.push.persistence.model.PushDeliveryAttemptRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

class PushDispatcherTest {
    private val deviceRepository = mock<DeviceRegistrationRepository>()
    private val pushClient = mock<ExpoPushClient>()
    private val attemptRepository = mock<PushDeliveryAttemptRepository>()
    private val dispatcher = PushDispatcher(deviceRepository, pushClient, attemptRepository)

    @Test
    fun `falls back to payment request registrations when order registrations are empty`() {
        val paymentRequestId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val event = paymentStatusChanged(paymentRequestId, orderId = "ORD-42")
        whenever(deviceRepository.findByOrderIdAndInvalidFalse("ORD-42")).thenReturn(emptyList())
        whenever(deviceRepository.findByPaymentRequestIdAndInvalidFalse(paymentRequestId)).thenReturn(
            listOf(deviceRegistration(orderId = "ORD-42", paymentRequestId = paymentRequestId))
        )
        whenever(pushClient.send(any())).thenReturn(emptyList())

        dispatcher.dispatch(event)

        verify(deviceRepository).findByOrderIdAndInvalidFalse("ORD-42")
        verify(deviceRepository).findByPaymentRequestIdAndInvalidFalse(paymentRequestId)
        verify(pushClient, times(1)).send(any())
    }

    @Test
    fun `does not fall back when order registrations are present`() {
        val paymentRequestId = UUID.fromString("11111111-1111-1111-1111-111111111112")
        val event = paymentStatusChanged(paymentRequestId, orderId = "ORD-42")
        whenever(deviceRepository.findByOrderIdAndInvalidFalse("ORD-42")).thenReturn(
            listOf(deviceRegistration(orderId = "ORD-42", paymentRequestId = paymentRequestId))
        )
        whenever(pushClient.send(any())).thenReturn(emptyList())

        dispatcher.dispatch(event)

        verify(deviceRepository).findByOrderIdAndInvalidFalse("ORD-42")
        verify(deviceRepository, never()).findByPaymentRequestIdAndInvalidFalse(any())
        verify(pushClient, times(1)).send(any())
    }

    @Test
    fun `falls back to payment request registrations when orderId is null`() {
        val paymentRequestId = UUID.fromString("11111111-1111-1111-1111-111111111113")
        val event = paymentStatusChanged(paymentRequestId, orderId = null)
        whenever(deviceRepository.findByPaymentRequestIdAndInvalidFalse(paymentRequestId)).thenReturn(
            listOf(deviceRegistration(orderId = "ORD-42", paymentRequestId = paymentRequestId))
        )
        whenever(pushClient.send(any())).thenReturn(emptyList())

        dispatcher.dispatch(event)

        verify(deviceRepository, never()).findByOrderIdAndInvalidFalse(any())
        verify(deviceRepository).findByPaymentRequestIdAndInvalidFalse(paymentRequestId)
        verify(pushClient, times(1)).send(any())
    }

    private fun paymentStatusChanged(paymentRequestId: UUID, orderId: String?) = PaymentStatusChanged(
        paymentRequestId = paymentRequestId,
        orderId = orderId,
        newStatus = PaymentStatusCode.SETTLED,
        previousStatus = PaymentStatusCode.PENDING,
        occurredAt = Instant.parse("2026-05-04T00:08:45Z"),
        sourceEventId = "stripe:evt_123",
    )

    private fun deviceRegistration(orderId: String, paymentRequestId: UUID) = DeviceRegistrationEntity(
        id = UUID.fromString("33333333-3333-3333-3333-333333333333"),
        paymentRequestId = paymentRequestId,
        orderId = orderId,
        expoPushToken = "ExponentPushToken[test-token]",
    )
}
