package com.elegant.software.blitzpay.usersettings.service

import com.elegant.software.blitzpay.merchant.api.AccountDeleted
import com.elegant.software.blitzpay.usersettings.domain.AppLanguage
import com.elegant.software.blitzpay.usersettings.persistence.repository.UserSettingRepository
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class UserSettingsResponse(
    val shopperAppLanguage: AppLanguage? = null,
    val merchantAppLanguage: AppLanguage? = null,
)

@Service
@Transactional
class UserSettingsService(private val repository: UserSettingRepository) {
    @Transactional(readOnly = true)
    fun get(userId: String): UserSettingsResponse {
        val values = repository.findAllByIdUserId(userId).associate { it.id.key to it.value }
        fun language(key: String) = AppLanguage.entries.firstOrNull { it.code == values[key] }
        return UserSettingsResponse(language("shopperAppLanguage"), language("merchantAppLanguage"))
    }

    fun update(userId: String, shopper: AppLanguage?, merchant: AppLanguage?): UserSettingsResponse {
        shopper?.let { repository.upsert(userId, "shopperAppLanguage", it.code) }
        merchant?.let { repository.upsert(userId, "merchantAppLanguage", it.code) }
        return get(userId)
    }

    @ApplicationModuleListener
    fun on(event: AccountDeleted) = repository.deleteAllByIdUserId(event.userId)
}
