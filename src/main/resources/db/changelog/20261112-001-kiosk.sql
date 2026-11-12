-- liquibase formatted sql

-- changeset mehdi:20261112-001-merchant-kiosks
-- One Kiosk per Branch: its shared Kiosk Login (password only as a BCrypt hash, never the password) and its Kiosk Settings.
CREATE TABLE blitzpay.merchant_kiosks (
    id UUID PRIMARY KEY,
    branch_id UUID NOT NULL REFERENCES blitzpay.merchant_branches (id),
    login VARCHAR(128),
    password_hash VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    payment_choices JSONB NOT NULL DEFAULT '["CARD","CASH","SCAN_TO_PAY"]'::jsonb,
    reset_seconds INTEGER NOT NULL DEFAULT 30,
    languages JSONB NOT NULL DEFAULT '[]'::jsonb,
    advertised_product_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_merchant_kiosks_reset_seconds CHECK (reset_seconds BETWEEN 5 AND 300)
);
CREATE UNIQUE INDEX ux_merchant_kiosks_branch_id ON blitzpay.merchant_kiosks (branch_id);
CREATE UNIQUE INDEX ux_merchant_kiosks_login ON blitzpay.merchant_kiosks (login);
-- rollback DROP TABLE blitzpay.merchant_kiosks;

-- changeset mehdi:20261112-002-merchant-kiosk-tokens
-- Opaque Kiosk session tokens, stored only as SHA-256 hashes; deleting them revokes the Kiosk.
CREATE TABLE blitzpay.merchant_kiosk_tokens (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    branch_id UUID NOT NULL REFERENCES blitzpay.merchant_branches (id),
    token_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX ux_merchant_kiosk_tokens_token_hash ON blitzpay.merchant_kiosk_tokens (token_hash);
CREATE INDEX ix_merchant_kiosk_tokens_branch_id ON blitzpay.merchant_kiosk_tokens (branch_id);
-- rollback DROP TABLE blitzpay.merchant_kiosk_tokens;

-- changeset mehdi:20261112-003-orders-origin
-- Where the Order was placed: STANDARD, or KIOSK for Orders from a restaurant-owned Kiosk.
ALTER TABLE blitzpay.order_orders ADD COLUMN origin VARCHAR(16) NOT NULL DEFAULT 'STANDARD';
-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN origin;
