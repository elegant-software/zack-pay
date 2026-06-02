package com.elegant.software.blitzpay.merchant.iam.listener

import com.elegant.software.blitzpay.merchant.api.BranchCreated
import com.elegant.software.blitzpay.merchant.api.BranchNameUpdated
import com.elegant.software.blitzpay.merchant.api.MerchantActivated
import com.elegant.software.blitzpay.merchant.api.MerchantNameUpdated
import com.elegant.software.blitzpay.merchant.iam.service.MerchantGroupSyncService
import org.springframework.context.annotation.Profile
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
@Profile("!contract-test")
class MerchantGroupSyncListener(private val syncService: MerchantGroupSyncService) {

    @ApplicationModuleListener
    fun on(event: MerchantActivated) { syncService.syncMerchant(event) }

    @ApplicationModuleListener
    fun on(event: BranchCreated) { syncService.syncBranch(event) }

    @ApplicationModuleListener
    fun on(event: MerchantNameUpdated) { syncService.updateMerchantName(event) }

    @ApplicationModuleListener
    fun on(event: BranchNameUpdated) { syncService.updateBranchName(event) }
}
