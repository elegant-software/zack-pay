package com.elegant.software.blitzpay.payments.adyen.config

import com.adyen.Client
import com.adyen.enums.Environment
import com.adyen.service.balanceplatform.AccountHoldersApi
import com.adyen.service.balanceplatform.BalanceAccountsApi
import com.adyen.service.legalentitymanagement.HostedOnboardingApi
import com.adyen.service.legalentitymanagement.LegalEntitiesApi
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableConfigurationProperties(AdyenProperties::class)
class AdyenConfig {

    @Bean
    @Profile("!contract-test")
    fun adyenClient(properties: AdyenProperties): Client {
        val environment = if (properties.environment.uppercase() == "LIVE") Environment.LIVE else Environment.TEST
        return if (properties.liveUrlPrefix.isNotBlank()) {
            Client(properties.apiKey, environment, properties.liveUrlPrefix)
        } else {
            Client(properties.apiKey, environment)
        }
    }

    @Bean
    @Profile("!contract-test")
    fun legalEntitiesApi(adyenClient: Client): LegalEntitiesApi = LegalEntitiesApi(adyenClient)

    @Bean
    @Profile("!contract-test")
    fun hostedOnboardingApi(adyenClient: Client): HostedOnboardingApi = HostedOnboardingApi(adyenClient)

    @Bean
    @Profile("!contract-test")
    fun accountHoldersApi(adyenClient: Client): AccountHoldersApi = AccountHoldersApi(adyenClient)

    @Bean
    @Profile("!contract-test")
    fun balanceAccountsApi(adyenClient: Client): BalanceAccountsApi = BalanceAccountsApi(adyenClient)
}
