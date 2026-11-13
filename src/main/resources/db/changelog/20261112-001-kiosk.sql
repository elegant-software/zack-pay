-- liquibase formatted sql

-- changeset mehdi:20261112-001-merchant-kiosk-settings
-- Kiosk Settings of a Branch, shared by all its Kiosks.
CREATE TABLE blitzpay.merchant_kiosk_settings (
    id UUID PRIMARY KEY,
    branch_id UUID NOT NULL REFERENCES blitzpay.merchant_branches (id),
    payment_choices JSONB NOT NULL DEFAULT '["CARD","CASH","SCAN_TO_PAY"]'::jsonb,
    reset_seconds INTEGER NOT NULL DEFAULT 30,
    languages JSONB NOT NULL DEFAULT '[]'::jsonb,
    advertised_product_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_merchant_kiosk_settings_reset_seconds CHECK (reset_seconds BETWEEN 5 AND 300)
);
CREATE UNIQUE INDEX ux_merchant_kiosk_settings_branch_id ON blitzpay.merchant_kiosk_settings (branch_id);
-- rollback DROP TABLE blitzpay.merchant_kiosk_settings;

-- changeset mehdi:20261112-002-merchant-kiosks
-- The Kiosks of a Branch. Each Kiosk Login is a Keycloak user (username); its password and enabled state live in Keycloak only.
CREATE TABLE blitzpay.merchant_kiosks (
    id UUID PRIMARY KEY,
    branch_id UUID NOT NULL REFERENCES blitzpay.merchant_branches (id),
    number INTEGER NOT NULL,
    name VARCHAR(100) NOT NULL,
    username VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX ux_merchant_kiosks_branch_number ON blitzpay.merchant_kiosks (branch_id, number);
CREATE UNIQUE INDEX ux_merchant_kiosks_username ON blitzpay.merchant_kiosks (username);
-- rollback DROP TABLE blitzpay.merchant_kiosks;

-- changeset mehdi:20261112-003-orders-origin
-- Where the Order was placed: STANDARD, or KIOSK for Orders from a restaurant-owned Kiosk.
ALTER TABLE blitzpay.order_orders ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'STANDARD';
-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN origin;
