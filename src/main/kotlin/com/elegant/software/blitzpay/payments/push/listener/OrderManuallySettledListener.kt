package com.elegant.software.blitzpay.payments.push.listener

import com.elegant.software.blitzpay.order.api.OrderManuallySettled
import com.elegant.software.blitzpay.payments.push.persistence.model.ShopperDeviceRegistrationRepository
import com.elegant.software.blitzpay.payments.push.service.ExpoMessage
import com.elegant.software.blitzpay.payments.push.service.ExpoPushClient
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/** Tells the shopper their cash (or other off-app) payment was confirmed, like the settled push a Terminal payment gets. */
@Component
class OrderManuallySettledListener(
    private val shopperDeviceRepository: ShopperDeviceRegistrationRepository,
    private val pushClient: ExpoPushClient,
) {
    private val log = LoggerFactory.getLogger(OrderManuallySettledListener::class.java)

    @ApplicationModuleListener
    fun on(event: OrderManuallySettled) {
        val devices = shopperDeviceRepository.findAllByShopperAccountIdAndInvalidFalse(event.shopperId)
        if (devices.isEmpty()) {
            log.info("no devices for settled order orderId={}", event.orderId)
            return
        }
        val messages = devices.map { device ->
            ExpoMessage(
                to = device.expoPushToken,
                title = "Payment settled",
                body = "Your payment has settled successfully.",
                data = mapOf("orderId" to event.orderId, "type" to "ORDER_PAID", "status" to "SETTLED"),
            )
        }
        try {
            pushClient.send(messages)
            log.info("order settled push sent orderId={} deviceCount={}", event.orderId, devices.size)
        } catch (ex: Exception) {
            log.error("order settled push failed orderId={}", event.orderId, ex)
        }
    }
}
