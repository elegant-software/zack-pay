package com.elegant.software.blitzpay.payments.push

import com.elegant.software.blitzpay.order.api.OrderManuallySettled
import com.elegant.software.blitzpay.payments.push.listener.OrderManuallySettledListener
import com.elegant.software.blitzpay.payments.push.persistence.model.ShopperDeviceRegistrationEntity
import com.elegant.software.blitzpay.payments.push.persistence.model.ShopperDeviceRegistrationRepository
import com.elegant.software.blitzpay.payments.push.service.ExpoMessage
import com.elegant.software.blitzpay.payments.push.service.ExpoPushClient
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

class OrderManuallySettledListenerTest {
    private val devices = mock<ShopperDeviceRegistrationRepository>()
    private val pushClient = mock<ExpoPushClient>()
    private val listener = OrderManuallySettledListener(devices, pushClient)

    @Test
    fun `sends the settled push to the shopper's devices`() {
        whenever(devices.findAllByShopperAccountIdAndInvalidFalse("shopper-1"))
            .thenReturn(listOf(ShopperDeviceRegistrationEntity(shopperAccountId = "shopper-1", expoPushToken = "ExponentPushToken[a]")))
        whenever(pushClient.send(any())).thenReturn(emptyList())

        listener.on(OrderManuallySettled("ORD-ABC", "shopper-1"))

        val captor = argumentCaptor<List<ExpoMessage>>()
        verify(pushClient).send(captor.capture())
        val message = captor.firstValue.single()
        assertEquals("ExponentPushToken[a]", message.to)
        assertEquals("ORDER_PAID", message.data["type"])
        assertEquals("ORD-ABC", message.data["orderId"])
    }

    @Test
    fun `skips shoppers without devices`() {
        whenever(devices.findAllByShopperAccountIdAndInvalidFalse("shopper-1")).thenReturn(emptyList())
        listener.on(OrderManuallySettled("ORD-ABC", "shopper-1"))
        verify(pushClient, never()).send(any())
    }
}
