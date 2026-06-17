package com.elegant.software.blitzpay.shopper.persistence

import com.elegant.software.blitzpay.shopper.domain.ShopperProfile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ShopperProfileRepository : JpaRepository<ShopperProfile, UUID> {
    fun findByShopperAccountId(shopperAccountId: String): ShopperProfile?
    fun deleteByShopperAccountId(shopperAccountId: String)
}
