package com.elegant.software.blitzpay.payments.push.listener

import com.elegant.software.blitzpay.order.api.InvoiceOrderCreated
import com.elegant.software.blitzpay.payments.push.persistence.model.ShopperDeviceRegistrationRepository
import com.elegant.software.blitzpay.payments.push.service.ExpoPushClient
import com.elegant.software.blitzpay.payments.push.service.ExpoMessage
import org.slf4j.LoggerFactory
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class InvoiceOrderCreatedListener(
    private val shopperDeviceRepository: ShopperDeviceRegistrationRepository,
    private val pushClient: ExpoPushClient,
) {
    private val log = LoggerFactory.getLogger(InvoiceOrderCreatedListener::class.java)

    @ApplicationModuleListener
    fun on(event: InvoiceOrderCreated) {
        val devices = shopperDeviceRepository.findAllByShopperAccountIdAndInvalidFalse(event.targetShopperId)
        if (devices.isEmpty()) {
            log.info("no devices for target shopper orderId={} shopperId={}", event.orderId, event.targetShopperId)
            return
        }

        val messages = devices.map { device ->
            ExpoMessage(
                to = device.expoPushToken,
                title = "New invoice",
                body = "You have a new invoice for ${event.currency} ${event.totalAmountMinor / 100.0}",
                data = mapOf("orderId" to event.orderId, "type" to "INVOICE_ORDER"),
            )
        }

        try {
            pushClient.send(messages)
            log.info("invoice order push sent orderId={} deviceCount={}", event.orderId, devices.size)
        } catch (ex: Exception) {
            log.error("invoice order push failed orderId={}", event.orderId, ex)
        }
    }
}
