import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should create stripe pay by bank checkout session and return checkout url'

    request {
        method POST()
        url '/v1/payments/stripe/pay-by-bank/create-checkout-session'
        headers {
            contentType(applicationJson())
        }
        body(
            orderId: 'ORDER-123',
            merchantId: '00000000-0000-0000-0000-000000000001',
            branchId: '00000000-0000-0000-0000-000000000002',
            successUrl: 'blitzpay://payment/success',
            cancelUrl: 'blitzpay://payment/cancel'
        )
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            paymentRequestId: $(consumer('123e4567-e89b-12d3-a456-426614174000'), producer(regex('[0-9a-fA-F\\-]{36}'))),
            checkoutUrl: 'https://checkout.stripe.com/pay/cs_test_123',
            checkoutSessionId: 'cs_test_123',
            connectedAccountId: 'acct_test_contract'
        )
    }
}
