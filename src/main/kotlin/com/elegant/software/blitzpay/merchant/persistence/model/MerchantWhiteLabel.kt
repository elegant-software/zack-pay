package com.elegant.software.blitzpay.merchant.persistence.model

import com.elegant.software.blitzpay.merchant.api.ThemeApp
import com.elegant.software.blitzpay.merchant.api.WhiteLabelSettings
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "merchant_white_label", schema = "blitzpay")
class MerchantWhiteLabel(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "merchant_id", nullable = false, updatable = false) val merchantId: UUID,
    @Column(name = "branch_id", updatable = false) val branchId: UUID? = null,
    @Enumerated(EnumType.STRING) @Column(nullable = false, updatable = false, length = 16) val app: ThemeApp,
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") var settings: WhiteLabelSettings = WhiteLabelSettings(),
    @Version var revision: Long = 0,
)
