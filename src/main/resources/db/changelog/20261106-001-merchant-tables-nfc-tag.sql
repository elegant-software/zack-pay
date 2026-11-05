-- liquibase formatted sql

-- changeset mehdi:20261106-001-merchant-tables-nfc-tag
-- When an NFC tag carrying the Table Link was last written for the Table; null when none was written.
ALTER TABLE blitzpay.merchant_tables ADD COLUMN nfc_tag_written_at TIMESTAMPTZ;
-- rollback ALTER TABLE blitzpay.merchant_tables DROP COLUMN nfc_tag_written_at;
