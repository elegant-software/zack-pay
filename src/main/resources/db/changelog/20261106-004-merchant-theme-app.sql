-- liquibase formatted sql

-- changeset codex:20261106-004-merchant-theme-app
ALTER TABLE blitzpay.merchant_theme
    ADD COLUMN app VARCHAR(16) NOT NULL DEFAULT 'SHOPPER'
    CHECK (app IN ('SHOPPER', 'MERCHANT'));
CREATE INDEX ix_merchant_theme_merchant_id_app ON blitzpay.merchant_theme (merchant_id, app);
-- rollback DROP INDEX blitzpay.ix_merchant_theme_merchant_id_app;
-- rollback ALTER TABLE blitzpay.merchant_theme DROP COLUMN app;
