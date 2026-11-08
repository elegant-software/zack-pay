package com.elegant.software.blitzpay.shopper.service

import com.elegant.software.blitzpay.merchant.api.AccountDeleted
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/** Erases the shopper profile of a deleted account. */
@Component
class AccountDeletedListener(private val profiles: ShopperProfileService) {
    @ApplicationModuleListener
    fun on(event: AccountDeleted) = profiles.delete(event.userId)
}
