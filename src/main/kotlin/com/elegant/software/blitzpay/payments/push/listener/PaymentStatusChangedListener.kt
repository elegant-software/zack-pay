package com.elegant.software.blitzpay.payments.push.listener

import com.elegant.software.blitzpay.order.api.PaymentStatusChanged
import com.elegant.software.blitzpay.payments.push.service.PushDispatcher
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class PaymentStatusChangedListener(
    private val dispatcher: PushDispatcher,
) {
    @ApplicationModuleListener
    fun on(event: PaymentStatusChanged) {
        dispatcher.dispatch(event)
    }
}
