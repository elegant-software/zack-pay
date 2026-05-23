-- liquibase formatted sql

-- changeset codex:20260525-001-stripe-connect-pay-by-bank
ALTER TABLE blitzpay.merchant_stripe_connect
    ADD COLUMN pay_by_bank_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN blitzpay.merchant_stripe_connect.pay_by_bank_enabled
    IS 'True once Stripe confirms via account.updated webhook that the connected account has active pay_by_bank_payments capability';

-- rollback ALTER TABLE blitzpay.merchant_stripe_connect DROP COLUMN pay_by_bank_enabled;
