package com.elegant.software.blitzpay.merchant.persistence.repository

import com.elegant.software.blitzpay.merchant.persistence.model.StorefrontTheme
import com.elegant.software.blitzpay.merchant.persistence.model.StorefrontThemeStatus
import org.springframework.data.jpa.repository.JpaRepository

interface StorefrontThemeRepository : JpaRepository<StorefrontTheme, String> {
    fun findAllByStatusOrderByDisplayName(status: StorefrontThemeStatus): List<StorefrontTheme>
}
