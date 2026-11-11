package com.elegant.software.blitzpay.usersettings.persistence.model

import jakarta.persistence.*
import java.io.Serializable

@Embeddable
data class UserSettingId(
    @Column(name = "user_id", length = 512) val userId: String = "",
    @Column(name = "setting_key", length = 100) val key: String = "",
) : Serializable

@Entity
@Table(name = "usersettings_preferences", schema = "blitzpay")
class UserSetting(
    @EmbeddedId val id: UserSettingId = UserSettingId(),
    @Column(name = "setting_value", nullable = false) val value: String = "",
)
