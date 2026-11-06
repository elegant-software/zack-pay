-- liquibase formatted sql

-- changeset codex:20261106-002-merchant-branding
ALTER TABLE blitzpay.merchants
    ADD COLUMN branding_enabled BOOLEAN,
    ADD COLUMN branding_display_name VARCHAR(120),
    ADD COLUMN branding_logo_storage_key VARCHAR(512),
    ADD COLUMN branding_primary_color VARCHAR(7),
    ADD COLUMN branding_accent_color VARCHAR(7);
ALTER TABLE blitzpay.merchant_branches
    ADD COLUMN branding_enabled BOOLEAN,
    ADD COLUMN branding_display_name VARCHAR(120),
    ADD COLUMN branding_logo_storage_key VARCHAR(512),
    ADD COLUMN branding_primary_color VARCHAR(7),
    ADD COLUMN branding_accent_color VARCHAR(7);
-- rollback ALTER TABLE blitzpay.merchant_branches DROP COLUMN branding_enabled, DROP COLUMN branding_display_name, DROP COLUMN branding_logo_storage_key, DROP COLUMN branding_primary_color, DROP COLUMN branding_accent_color;
-- rollback ALTER TABLE blitzpay.merchants DROP COLUMN branding_enabled, DROP COLUMN branding_display_name, DROP COLUMN branding_logo_storage_key, DROP COLUMN branding_primary_color, DROP COLUMN branding_accent_color;
