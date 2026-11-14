-- liquibase formatted sql

-- changeset mehdi:20261114-001-kiosk-admin-pin
-- BCrypt hash of the Branch's Kiosk Admin PIN, which unlocks the admin menu on its Kiosks. Null = no PIN set.
ALTER TABLE blitzpay.merchant_kiosk_settings ADD COLUMN admin_pin_hash VARCHAR(100);
-- rollback ALTER TABLE blitzpay.merchant_kiosk_settings DROP COLUMN admin_pin_hash;
