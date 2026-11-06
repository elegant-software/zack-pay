package com.elegant.software.blitzpay.merchant.persistence.model

import com.elegant.software.blitzpay.merchant.api.PreparationConfiguration
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "merchant_preparation_configuration", schema = "blitzpay")
class MerchantPreparationConfiguration(
    @Id
    @Column(name = "branch_id")
    val branchId: UUID,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "configuration", nullable = false, columnDefinition = "jsonb")
    var configuration: PreparationConfiguration = PreparationConfiguration(),
    @Version
    var version: Long? = null,
)
