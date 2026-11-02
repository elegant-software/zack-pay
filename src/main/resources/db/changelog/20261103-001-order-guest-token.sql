-- liquibase formatted sql

-- changeset mehdi:20261103-001-order-guest-token
-- Web storefront guest checkout: an order-scoped capability token. Only the SHA-256 hash is stored.
ALTER TABLE blitzpay.order_orders
    ADD COLUMN guest_token_hash       VARCHAR(64),
    ADD COLUMN guest_token_expires_at TIMESTAMPTZ;
-- rollback ALTER TABLE blitzpay.order_orders DROP COLUMN guest_token_expires_at, DROP COLUMN guest_token_hash;
