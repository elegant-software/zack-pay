package com.elegant.software.blitzpay.usersettings.persistence.repository

import com.elegant.software.blitzpay.usersettings.persistence.model.UserSetting
import com.elegant.software.blitzpay.usersettings.persistence.model.UserSettingId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface UserSettingRepository : JpaRepository<UserSetting, UserSettingId> {
    fun findAllByIdUserId(userId: String): List<UserSetting>
    fun deleteAllByIdUserId(userId: String)

    @Modifying
    @Query(value = """
        INSERT INTO blitzpay.usersettings_preferences (user_id, setting_key, setting_value)
        VALUES (:userId, :key, :value)
        ON CONFLICT (user_id, setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value
    """, nativeQuery = true)
    fun upsert(userId: String, key: String, value: String)
}
