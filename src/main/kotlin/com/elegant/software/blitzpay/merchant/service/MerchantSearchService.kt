package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.MerchantSearchRequest
import com.elegant.software.blitzpay.merchant.api.MerchantSearchResponse
import com.elegant.software.blitzpay.merchant.api.MerchantSearchResultItem
import com.elegant.software.blitzpay.merchant.repository.MerchantApplicationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MerchantSearchService(
    private val repository: MerchantApplicationRepository,
) {

    @Transactional(readOnly = true)
    fun search(request: MerchantSearchRequest): MerchantSearchResponse {
        val size = request.size.coerceIn(1, 100)
        val page = PageRequest.of(
            request.page.coerceAtLeast(0),
            size,
            Sort.by(Sort.Direction.DESC, "lastUpdatedAt"),
        )
        val spec = MerchantSearchSpecification.from(request)
        val result = repository.findAll(spec, page)
        return MerchantSearchResponse(
            results = result.content.map { m ->
                MerchantSearchResultItem(
                    applicationId = m.id,
                    merchantName = m.merchantName.ifBlank { m.businessProfile.legalBusinessName },
                    legalBusinessName = m.businessProfile.legalBusinessName,
                    merchantCode = m.merchantCode,
                    city = m.address?.city,
                    country = m.address?.country,
                    businessType = m.businessProfile.businessType,
                    merchantMobility = m.merchantMobility.name,
                    status = m.status.name,
                    lastUpdatedAt = m.lastUpdatedAt,
                )
            },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }
}
