-- liquibase formatted sql

-- changeset codex:20261106-003-merchant-themes
CREATE TABLE blitzpay.merchant_theme (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES blitzpay.merchants(id) ON DELETE RESTRICT,
    name VARCHAR(120) NOT NULL CHECK (length(trim(name)) > 0),
    primary_color VARCHAR(7) NOT NULL CHECK (primary_color ~ '^#[0-9A-Fa-f]{6}$'),
    accent_color VARCHAR(7) NOT NULL CHECK (accent_color ~ '^#[0-9A-Fa-f]{6}$')
);
CREATE INDEX ix_merchant_theme_merchant_id ON blitzpay.merchant_theme (merchant_id);
-- rollback DROP TABLE blitzpay.merchant_theme;
