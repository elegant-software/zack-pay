-- liquibase formatted sql

-- changeset mehdi:20261108-002-merchant-storefront-theme
-- Named storefront look per merchant (StorefrontTheme enum); the storefront maps each value to a CSS preset.
ALTER TABLE blitzpay.merchants ADD COLUMN storefront_theme VARCHAR(32) NOT NULL DEFAULT 'DEFAULT';
-- rollback ALTER TABLE blitzpay.merchants DROP COLUMN storefront_theme;
