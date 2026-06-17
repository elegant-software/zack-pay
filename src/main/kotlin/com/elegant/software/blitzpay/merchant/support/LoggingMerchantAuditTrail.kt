package com.elegant.software.blitzpay.merchant.support

import com.elegant.software.blitzpay.merchant.service.MerchantAuditEvent
import com.elegant.software.blitzpay.merchant.service.MerchantAuditTrail
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LoggingMerchantAuditTrail : MerchantAuditTrail {
    private val logger = LoggerFactory.getLogger(LoggingMerchantAuditTrail::class.java)

    override fun record(event: MerchantAuditEvent) {
        logger.info(
            "merchant_audit action={} actorId={} applicationId={} " +
                "applicationReference={} status={} details={}",
            event.action, event.actorId, event.applicationId,
            event.applicationReference, event.status, event.details
        )
    }
}
