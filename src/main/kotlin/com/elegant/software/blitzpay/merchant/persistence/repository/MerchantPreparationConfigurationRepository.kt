package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.MerchantPreparationConfiguration
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface MerchantPreparationConfigurationRepository : JpaRepository<MerchantPreparationConfiguration, UUID>
