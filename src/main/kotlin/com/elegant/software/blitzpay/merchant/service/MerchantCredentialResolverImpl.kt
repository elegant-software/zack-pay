package com.elegant.software.blitzpay.merchant.service

import com.elegant.software.blitzpay.merchant.api.BraintreeCredentials
import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.merchant.api.StripeCredentials
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProviderAccountRepository
import com.elegant.software.blitzpay.merchant.persistence.repository.MerchantProductRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class MerchantCredentialResolverImpl(
    private val providerAccountRepository: MerchantProviderAccountRepository,
    private val merchantProductRepository: MerchantProductRepository,
    @Value("\${stripe.secret-key:}") private val stripeSecretKey: String,
    @Value("\${stripe.publishable-key:}") private val stripePublishableKey: String,
    @Value("\${braintree.merchant-id:}") private val btMerchantId: String,
    @Value("\${braintree.public-key:}") private val btPublicKey: String,
    @Value("\${braintree.private-key:}") private val btPrivateKey: String,
    @Value("\${braintree.environment:sandbox}") private val btEnvironment: String,
) : MerchantCredentialResolver {

    override fun resolveStripe(merchantId: UUID, branchId: UUID?): StripeCredentials? {
        if (!providerAccountRepository.existsByMerchantIdAndPspCodeAndEnabledTrue(merchantId, "STRIPE")) {
            return null
        }
        if (stripeSecretKey.isNotBlank() && stripePublishableKey.isNotBlank()) {
            return StripeCredentials(
                secretKey = stripeSecretKey,
                publishableKey = stripePublishableKey,
            )
        }
        return null
    }

    override fun resolveBraintree(merchantId: UUID, branchId: UUID?): BraintreeCredentials? {
        if (!providerAccountRepository.existsByMerchantIdAndPspCodeAndEnabledTrue(merchantId, "PAYPAL")) {
            return null
        }
        if (btMerchantId.isNotBlank() && btPublicKey.isNotBlank() && btPrivateKey.isNotBlank()) {
            return BraintreeCredentials(
                merchantId = btMerchantId,
                publicKey = btPublicKey,
                privateKey = btPrivateKey,
                environment = btEnvironment,
            )
        }
        return null
    }

    override fun resolveBranch(merchantId: UUID, branchId: UUID?, productId: UUID?): UUID? {
        if (branchId != null) {
            return branchId
        }
        if (productId != null) {
            return merchantProductRepository.findByIdAndActiveTrue(productId).orElse(null)?.merchantBranchId
        }
        return null
    }

    override fun resolveProductPrice(productId: UUID): BigDecimal? =
        merchantProductRepository.findByIdAndActiveTrue(productId).orElse(null)?.unitPrice
}
