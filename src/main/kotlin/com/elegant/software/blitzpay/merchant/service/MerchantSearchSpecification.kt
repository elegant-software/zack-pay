package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.MerchantSearchRequest
import com.elegant.software.blitzpay.merchant.domain.BusinessProfile
import com.elegant.software.blitzpay.merchant.domain.MerchantApplication
import com.elegant.software.blitzpay.merchant.domain.MerchantBranch
import com.elegant.software.blitzpay.merchant.domain.MerchantMobility
import com.elegant.software.blitzpay.merchant.domain.MerchantOnboardingStatus
import com.elegant.software.blitzpay.merchant.domain.MerchantProduct
import com.elegant.software.blitzpay.merchant.domain.PostalAddress
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

object MerchantSearchSpecification {

    fun from(request: MerchantSearchRequest): Specification<MerchantApplication> =
        Specification { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

            val q = request.q?.trim()
            if (!q.isNullOrEmpty()) {
                val like = "%${q.lowercase()}%"
                val textPreds = mutableListOf<Predicate>()

                // Code and reference fields: matched at any length
                textPreds += cb.like(cb.lower(root.get("merchantCode")), like)
                textPreds += cb.like(cb.lower(root.get("applicationReference")), like)
                textPreds += cb.like(cb.lower(root.get("publicEmail")), like)

                if (q.length >= 3) {
                    // Name fields
                    textPreds += cb.like(cb.lower(root.get("merchantName")), like)
                    val biz = root.get<BusinessProfile>("businessProfile")
                    textPreds += cb.like(cb.lower(biz.get("legalBusinessName")), like)
                    textPreds += cb.like(cb.lower(biz.get("primaryBusinessAddress")), like)

                    // Address columns on merchant_applications
                    val addr = root.get<PostalAddress>("address")
                    textPreds += cb.like(cb.lower(addr.get("addressLine1")), like)
                    textPreds += cb.like(cb.lower(addr.get("city")), like)
                    textPreds += cb.like(cb.lower(addr.get("postalCode")), like)
                    textPreds += cb.like(cb.lower(addr.get("country")), like)

                    // Branch name + address via EXISTS subquery (avoids duplicate merchant rows)
                    val branchSubq = query.subquery(UUID::class.java)
                    val branch = branchSubq.from(MerchantBranch::class.java)
                    branchSubq.select(branch.get("merchantApplicationId"))
                    val branchAddr = branch.get<PostalAddress>("address")
                    branchSubq.where(
                        cb.equal(branch.get<UUID>("merchantApplicationId"), root.get<UUID>("id")),
                        cb.or(
                            cb.like(cb.lower(branch.get("name")), like),
                            cb.like(cb.lower(branchAddr.get("addressLine1")), like),
                            cb.like(cb.lower(branchAddr.get("city")), like),
                        )
                    )
                    textPreds += cb.exists(branchSubq)

                    // Product name via EXISTS subquery
                    val productSubq = query.subquery(UUID::class.java)
                    val product = productSubq.from(MerchantProduct::class.java)
                    productSubq.select(product.get("merchantApplicationId"))
                    productSubq.where(
                        cb.equal(product.get<UUID>("merchantApplicationId"), root.get<UUID>("id")),
                        cb.like(cb.lower(product.get("name")), like)
                    )
                    textPreds += cb.exists(productSubq)
                }

                predicates += cb.or(*textPreds.toTypedArray())
            }

            // businessType is stored as a plain String column in BusinessProfile
            if (!request.businessTypes.isNullOrEmpty()) {
                predicates += root.get<BusinessProfile>("businessProfile")
                    .get<String>("businessType").`in`(request.businessTypes)
            }

            if (!request.merchantMobilities.isNullOrEmpty()) {
                val mobilities = request.merchantMobilities.map { MerchantMobility.valueOf(it) }
                predicates += root.get<MerchantMobility>("merchantMobility").`in`(mobilities)
            }

            if (!request.statuses.isNullOrEmpty()) {
                val statusList = request.statuses.map { MerchantOnboardingStatus.valueOf(it) }
                predicates += root.get<MerchantOnboardingStatus>("status").`in`(statusList)
            }

            if (predicates.isEmpty()) cb.conjunction() else cb.and(*predicates.toTypedArray())
        }
}
