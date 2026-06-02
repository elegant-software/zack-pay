package com.elegant.software.blitzpay.merchant.iam.outbound

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpRequest
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

class KeycloakBearerTokenInterceptor(private val tokenProvider: KeycloakTokenProvider) : ClientHttpRequestInterceptor {

    private val log = LoggerFactory.getLogger(KeycloakBearerTokenInterceptor::class.java)

    override fun intercept(request: HttpRequest, body: ByteArray, execution: ClientHttpRequestExecution): ClientHttpResponse {
        request.headers.set(HttpHeaders.AUTHORIZATION, "Bearer ${tokenProvider.getToken()}")
        val response = execution.execute(request, body)
        if (response.statusCode == HttpStatus.UNAUTHORIZED) {
            log.warn("keycloak returned 401, retrying with fresh token method={} uri={}", request.method, request.uri)
            response.close()
            tokenProvider.invalidate()
            request.headers.set(HttpHeaders.AUTHORIZATION, "Bearer ${tokenProvider.getToken()}")
            return execution.execute(request, body)
        }
        return response
    }
}
