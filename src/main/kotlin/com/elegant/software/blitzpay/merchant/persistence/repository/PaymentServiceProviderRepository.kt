package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.PaymentServiceProvider
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentServiceProviderRepository : JpaRepository<PaymentServiceProvider, String>
