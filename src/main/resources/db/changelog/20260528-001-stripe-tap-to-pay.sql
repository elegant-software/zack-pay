--liquibase formatted sql

--changeset blitzpay:20260528-001-stripe-tap-to-pay
ALTER TABLE blitzpay.merchant_stripe_provider_accounts
    ADD COLUMN tap_to_pay_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE blitzpay.merchant_branches
    ADD COLUMN stripe_terminal_location_id VARCHAR(255);
