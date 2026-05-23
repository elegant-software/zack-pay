import org.springframework.cloud.contract.spec.Contract

Contract.make {
    description 'should return stripe connect status including pay by bank flow'

    request {
        method GET()
        url '/v1/merchants/00000000-0000-0000-0000-000000000001/stripe-connect/status'
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            connected: true,
            chargesEnabled: true,
            detailsSubmitted: true,
            enabledPaymentFlows: [
                'CARD',
                'PAY_BY_BANK'
            ]
        )
    }
}
