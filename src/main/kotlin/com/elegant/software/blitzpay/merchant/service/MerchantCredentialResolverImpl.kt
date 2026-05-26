package com.elegant.software.blitzpay.merchant.application

import com.elegant.software.blitzpay.merchant.api.BraintreeCredentials
import com.elegant.software.blitzpay.merchant.api.MerchantCredentialResolver
import com.elegant.software.blitzpay.merchant.api.StripeCredentials
import com.elegant.software.blitzpay.merchant.repository.MerchantProviderAccountRepository
import com.elegant.software.blitzpay.merchant.repository.MerchantProductRepository
import com.elegant.software.blitzpay.payments.braintree.config.BraintreeProperties
import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.util.UUID

@Service
class MerchantCredentialResolverImpl(
    private val providerAccountRepository: MerchantProviderAccountRepository,
    private val merchantProductRepository: MerchantProductRepository,
    private val stripeProperties: StripeProperties,
    private val braintreeProperties: BraintreeProperties,
) : MerchantCredentialResolver {

    override fun resolveStripe(merchantId: UUID, branchId: UUID?): StripeCredentials? {
        if (!providerAccountRepository.existsByMerchantApplicationIdAndPspCodeAndEnabledTrue(merchantId, "STRIPE")) {
            return null
        }
        if (stripeProperties.secretKey.isNotBlank() && stripeProperties.publishableKey.isNotBlank()) {
            return StripeCredentials(
                secretKey = stripeProperties.secretKey,
                publishableKey = stripeProperties.publishableKey
            )
        }
        return null
    }

    override fun resolveBraintree(merchantId: UUID, branchId: UUID?): BraintreeCredentials? {
        if (!providerAccountRepository.existsByMerchantApplicationIdAndPspCodeAndEnabledTrue(merchantId, "PAYPAL")) {
            return null
        }
        if (braintreeProperties.merchantId.isNotBlank() &&
            braintreeProperties.publicKey.isNotBlank() &&
            braintreeProperties.privateKey.isNotBlank()
        ) {
            return BraintreeCredentials(
                merchantId = braintreeProperties.merchantId,
                publicKey = braintreeProperties.publicKey,
                privateKey = braintreeProperties.privateKey,
                environment = braintreeProperties.environment,
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
