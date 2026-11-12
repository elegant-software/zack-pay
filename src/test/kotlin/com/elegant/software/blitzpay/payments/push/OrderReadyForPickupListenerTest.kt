package com.elegant.software.blitzpay.payments.push

import com.elegant.software.blitzpay.order.api.FulfilmentType
import com.elegant.software.blitzpay.order.api.OrderReadyForPickup
import com.elegant.software.blitzpay.payments.push.listener.OrderReadyForPickupListener
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
import java.util.UUID
import kotlin.test.assertEquals

class OrderReadyForPickupListenerTest {
    private val devices = mock<ShopperDeviceRegistrationRepository>()
    private val pushClient = mock<ExpoPushClient>()
    private val listener = OrderReadyForPickupListener(devices, pushClient)

    private fun event(pickupNumber: Int?) = OrderReadyForPickup("ORD-ABC", UUID.randomUUID(), UUID.randomUUID(), pickupNumber, "shopper-1")

    @Test
    fun `sends the ready push with the padded pickup number to the shopper's devices`() {
        whenever(devices.findAllByShopperAccountIdAndInvalidFalse("shopper-1"))
            .thenReturn(listOf(ShopperDeviceRegistrationEntity(shopperAccountId = "shopper-1", expoPushToken = "ExponentPushToken[a]")))
        whenever(pushClient.send(any())).thenReturn(emptyList())

        listener.on(event(42))

        val captor = argumentCaptor<List<ExpoMessage>>()
        verify(pushClient).send(captor.capture())
        assertEquals("Order #042 is ready, please collect it at the counter", captor.firstValue.single().body)
        assertEquals("ExponentPushToken[a]", captor.firstValue.single().to)
    }

    @Test
    fun `tells a dine-in shopper at a Table the order is on its way`() {
        val e = OrderReadyForPickup("ORD-ABC", UUID.randomUUID(), UUID.randomUUID(), 7, "shopper-1", FulfilmentType.DINE_IN, atTable = true)
        assertEquals("Order #007 is on its way to your table", OrderReadyForPickupListener.readyMessage(e))
        assertEquals(
            "Order #007 is ready, please collect it at the counter",
            OrderReadyForPickupListener.readyMessage(e.copy(fulfilmentType = FulfilmentType.TAKE_AWAY)),
        )
    }

    @Test
    fun `falls back to the order code and skips shoppers without devices`() {
        assertEquals("ORD-ABC", OrderReadyForPickupListener.orderLabel(event(null)))
        whenever(devices.findAllByShopperAccountIdAndInvalidFalse("shopper-1")).thenReturn(emptyList())
        listener.on(event(1))
        verify(pushClient, never()).send(any())
    }
}
