-- liquibase formatted sql

-- changeset mehdi:20260610-002-payment-settlement-reconciliation
ALTER TABLE blitzpay.payment_settlements
    ADD COLUMN funds_available_at TIMESTAMPTZ NULL,
    ADD COLUMN provider_payment_reference VARCHAR(255) NULL,
    ADD COLUMN provider_dashboard_url VARCHAR(1000) NULL;

-- rollback ALTER TABLE blitzpay.payment_settlements DROP COLUMN provider_dashboard_url, DROP COLUMN provider_payment_reference, DROP COLUMN funds_available_at;
