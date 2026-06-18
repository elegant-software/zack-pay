package com.elegant.software.blitzpay.shopper.service

import com.elegant.software.blitzpay.shopper.api.ShopperProfileData
import com.elegant.software.blitzpay.shopper.api.ShopperProfileGateway
import com.elegant.software.blitzpay.shopper.api.ShopperSearchResult
import com.elegant.software.blitzpay.shopper.domain.ShopperProfile
import com.elegant.software.blitzpay.shopper.persistence.ShopperProfileRepository
import com.elegant.software.blitzpay.shopper.web.UpsertShopperProfileRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ShopperProfileService(
    private val repository: ShopperProfileRepository,
) : ShopperProfileGateway {

    private val log = LoggerFactory.getLogger(ShopperProfileService::class.java)

    @Transactional(readOnly = true)
    override fun findByShopperId(shopperId: String): ShopperProfileData? =
        repository.findByShopperAccountId(shopperId)?.toData()

    @Transactional(readOnly = true)
    override fun findByEmail(email: String): ShopperSearchResult? =
        repository.findByEmail(email)?.let { ShopperSearchResult(it.shopperAccountId, it.name) }

    @Transactional(readOnly = true)
    fun get(shopperId: String): ShopperProfileData? =
        repository.findByShopperAccountId(shopperId)?.toData()

    @Transactional
    fun upsert(shopperId: String, request: UpsertShopperProfileRequest): ShopperProfileData {
        val existing = repository.findByShopperAccountId(shopperId)
        val profile = if (existing != null) {
            existing.name = request.name
            existing.street = request.street
            existing.zip = request.zip
            existing.city = request.city
            existing.country = request.country
            existing.email = request.email ?: existing.email
            existing.updatedAt = Instant.now()
            existing
        } else {
            ShopperProfile(
                shopperAccountId = shopperId,
                name = request.name,
                street = request.street,
                zip = request.zip,
                city = request.city,
                country = request.country,
                email = request.email,
            )
        }
        val saved = repository.save(profile)
        log.info("shopper profile upserted shopperId={}", shopperId)
        return saved.toData()
    }

    @Transactional
    fun delete(shopperId: String) {
        repository.deleteByShopperAccountId(shopperId)
        log.info("shopper profile deleted shopperId={}", shopperId)
    }

    private fun ShopperProfile.toData() = ShopperProfileData(
        name = name,
        street = street,
        zip = zip,
        city = city,
        country = country,
        email = email,
    )
}
