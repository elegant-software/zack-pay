package com.elegant.software.blitzpay.payments.stripe.internal

import com.elegant.software.blitzpay.payments.stripe.config.StripeProperties
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/stripe-connect")
class StripeConnectReturnController(
    private val stripeProperties: StripeProperties,
) {
    @GetMapping("/return", produces = [MediaType.TEXT_HTML_VALUE])
    fun onReturn(): ResponseEntity<String> {
        val deepLink = stripeProperties.connect.appDeepLink.takeIf { it.isNotBlank() }
        return if (deepLink != null) {
            ResponseEntity.status(HttpStatus.FOUND).location(URI.create(deepLink)).build()
        } else {
            ResponseEntity.ok(
                """<!DOCTYPE html>
<html lang="en">
<head><meta charset="UTF-8"><title>Onboarding Complete</title>
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>body{font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:100vh;margin:0;background:#f5f5f5}
.card{background:#fff;border-radius:12px;padding:40px;text-align:center;box-shadow:0 2px 12px rgba(0,0,0,.1);max-width:360px}
h1{color:#2e7d32;font-size:1.4rem;margin-bottom:8px}p{color:#555;font-size:.95rem}</style></head>
<body><div class="card">
<h1>&#10003; Onboarding complete</h1>
<p>Your Stripe account is set up. You can close this page and return to the app.</p>
</div></body></html>"""
            )
        }
    }
}
