package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.MerchantSearchRequest
import com.elegant.software.blitzpay.merchant.persistence.model.Merchant
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantBranch
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantMobility
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantOperationalStatus
import com.elegant.software.blitzpay.merchant.persistence.model.MerchantProduct
import com.elegant.software.blitzpay.merchant.persistence.model.PostalAddress
import jakarta.persistence.criteria.Predicate
import org.springframework.data.jpa.domain.Specification
import java.util.UUID

object MerchantSearchSpecification {

    fun from(request: MerchantSearchRequest): Specification<Merchant> =
        Specification { root, query, cb ->
            val predicates = mutableListOf<Predicate>()

            val q = request.q?.trim()
            if (!q.isNullOrEmpty()) {
                val like = "%${q.lowercase()}%"
                val normalizedCodeLike = "%${q.lowercase().replace("-", "")}%"
                val textPreds = mutableListOf<Predicate>()

                // Code fields: matched at any length
                textPreds += cb.like(cb.lower(root.get("merchantCode")), like)
                textPreds += cb.like(
                    cb.lower(cb.function("replace", String::class.java, root.get<String>("merchantCode"), cb.literal("-"), cb.literal(""))),
                    normalizedCodeLike
                )
                textPreds += cb.like(cb.lower(root.get("publicEmail")), like)

                if (q.length >= 3) {
                    textPreds += cb.like(cb.lower(root.get("merchantName")), like)

                    // Address columns on merchants
                    val addr = root.get<PostalAddress>("address")
                    textPreds += cb.like(cb.lower(addr.get("addressLine1")), like)
                    textPreds += cb.like(cb.lower(addr.get("city")), like)
                    textPreds += cb.like(cb.lower(addr.get("postalCode")), like)
                    textPreds += cb.like(cb.lower(addr.get("country")), like)

                    // Branch name + address via EXISTS subquery (avoids duplicate merchant rows)
                    val branchSubq = query.subquery(UUID::class.java)
                    val branch = branchSubq.from(MerchantBranch::class.java)
                    branchSubq.select(branch.get("merchantId"))
                    val branchAddr = branch.get<PostalAddress>("address")
                    branchSubq.where(
                        cb.equal(branch.get<UUID>("merchantId"), root.get<UUID>("id")),
                        cb.or(
                            cb.like(cb.lower(branch.get("branchCode")), like),
                            cb.like(
                                cb.lower(cb.function("replace", String::class.java, branch.get<String>("branchCode"), cb.literal("-"), cb.literal(""))),
                                normalizedCodeLike
                            ),
                            cb.like(cb.lower(branch.get("name")), like),
                            cb.like(cb.lower(branchAddr.get("addressLine1")), like),
                            cb.like(cb.lower(branchAddr.get("city")), like),
                        )
                    )
                    textPreds += cb.exists(branchSubq)

                    // Product name via EXISTS subquery
                    val productSubq = query.subquery(UUID::class.java)
                    val product = productSubq.from(MerchantProduct::class.java)
                    productSubq.select(product.get("merchantId"))
                    productSubq.where(
                        cb.equal(product.get<UUID>("merchantId"), root.get<UUID>("id")),
                        cb.like(cb.lower(product.get("name")), like)
                    )
                    textPreds += cb.exists(productSubq)
                }

                predicates += cb.or(*textPreds.toTypedArray())
            }

            if (!request.businessTypes.isNullOrEmpty()) {
                predicates += root.get<String>("businessType").`in`(request.businessTypes)
            }

            if (!request.merchantMobilities.isNullOrEmpty()) {
                predicates += root.get<MerchantMobility>("merchantMobility").`in`(request.merchantMobilities)
            }

            if (!request.statuses.isNullOrEmpty()) {
                val statusList = request.statuses.map { MerchantOperationalStatus.valueOf(it) }
                predicates += root.get<MerchantOperationalStatus>("merchantStatus").`in`(statusList)
            }

            if (predicates.isEmpty()) cb.conjunction() else cb.and(*predicates.toTypedArray())
        }
}
