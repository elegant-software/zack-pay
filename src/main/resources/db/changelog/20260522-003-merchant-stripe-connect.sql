-- liquibase formatted sql

-- changeset mohsenjd:20260522-003-create-merchant-stripe-connect
CREATE TABLE blitzpay.merchant_stripe_connect (
    merchant_id           UUID        NOT NULL,
    connected_account_id  VARCHAR(64) NOT NULL,
    charges_enabled       BOOLEAN     NOT NULL DEFAULT FALSE,
    details_submitted     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_merchant_stripe_connect PRIMARY KEY (merchant_id),
    CONSTRAINT fk_stripe_connect_merchant FOREIGN KEY (merchant_id)
        REFERENCES blitzpay.merchant_applications (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_stripe_connect_account_id ON blitzpay.merchant_stripe_connect (connected_account_id);

COMMENT ON TABLE blitzpay.merchant_stripe_connect IS 'Stripe Connect Express account state per Merchant; one row per merchant, created when the merchant enables Stripe as a payment channel';
COMMENT ON COLUMN blitzpay.merchant_stripe_connect.connected_account_id IS 'Stripe-issued acct_XXXX identifier for the merchant Express account';
COMMENT ON COLUMN blitzpay.merchant_stripe_connect.charges_enabled      IS 'True once Stripe confirms via account.updated webhook that the account can process live charges';
COMMENT ON COLUMN blitzpay.merchant_stripe_connect.details_submitted    IS 'True once the merchant has submitted the Connect onboarding form to Stripe';

-- rollback DROP INDEX blitzpay.idx_stripe_connect_account_id;
-- rollback DROP TABLE blitzpay.merchant_stripe_connect;