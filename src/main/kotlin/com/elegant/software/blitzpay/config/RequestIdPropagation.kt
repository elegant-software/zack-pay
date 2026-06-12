package com.elegant.software.blitzpay.config

import org.slf4j.MDC
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.web.client.RestClient
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.WebClient

fun WebClient.Builder.propagateRequestId(): WebClient.Builder =
    filter { request, next ->
        val requestId = MDC.get(LogContext.REQUEST_ID)
        if (requestId.isNullOrBlank()) {
            next.exchange(request)
        } else {
            next.exchange(
                ClientRequest.from(request)
                    .header(RequestIdWebFilter.REQUEST_ID_HEADER, requestId)
                    .build()
            )
        }
    }

fun RestClient.Builder.propagateRequestId(): RestClient.Builder =
    requestInterceptor(
        ClientHttpRequestInterceptor { request, body, execution ->
            MDC.get(LogContext.REQUEST_ID)
                ?.takeIf(String::isNotBlank)
                ?.let { request.headers.set(RequestIdWebFilter.REQUEST_ID_HEADER, it) }
            execution.execute(request, body)
        }
    )
