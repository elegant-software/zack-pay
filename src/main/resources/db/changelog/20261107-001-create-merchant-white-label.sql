-- liquibase formatted sql
-- changeset codex:20261107-001-create-merchant-white-label
ALTER TABLE blitzpay.merchant_theme ADD COLUMN light JSONB, ADD COLUMN dark JSONB;
ALTER TABLE blitzpay.merchant_theme DROP CONSTRAINT merchant_theme_app_check;
ALTER TABLE blitzpay.merchant_theme ADD CONSTRAINT merchant_theme_app_check
    CHECK (app IN ('SHOPPER', 'MERCHANT', 'STOREFRONT', 'KITCHEN'));
CREATE TABLE blitzpay.merchant_white_label (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL REFERENCES blitzpay.merchants(id) ON DELETE RESTRICT,
    branch_id UUID REFERENCES blitzpay.merchant_branches(id) ON DELETE RESTRICT,
    app VARCHAR(16) NOT NULL CHECK (app IN ('SHOPPER', 'MERCHANT', 'STOREFRONT', 'KITCHEN')),
    settings JSONB NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_merchant_white_label_merchant_app ON blitzpay.merchant_white_label (merchant_id, app) WHERE branch_id IS NULL;
CREATE UNIQUE INDEX ux_merchant_white_label_branch_app ON blitzpay.merchant_white_label (merchant_id, branch_id, app) WHERE branch_id IS NOT NULL;
-- rollback DROP TABLE blitzpay.merchant_white_label;
-- rollback ALTER TABLE blitzpay.merchant_theme DROP CONSTRAINT merchant_theme_app_check;
-- rollback ALTER TABLE blitzpay.merchant_theme ADD CONSTRAINT merchant_theme_app_check CHECK (app IN ('SHOPPER', 'MERCHANT'));
-- rollback ALTER TABLE blitzpay.merchant_theme DROP COLUMN light, DROP COLUMN dark;
