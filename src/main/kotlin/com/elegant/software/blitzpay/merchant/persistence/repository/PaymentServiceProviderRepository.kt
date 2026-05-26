package com.elegant.software.blitzpay.merchant.repository

import com.elegant.software.blitzpay.merchant.domain.PaymentServiceProvider
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentServiceProviderRepository : JpaRepository<PaymentServiceProvider, String>
