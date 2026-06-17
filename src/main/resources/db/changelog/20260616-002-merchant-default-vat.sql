-- liquibase formatted sql

-- changeset mehdi:20260616-002-merchant-default-vat
ALTER TABLE blitzpay.merchant_applications
    ADD COLUMN default_vat_percent NUMERIC(5,2) NULL;

-- rollback ALTER TABLE blitzpay.merchant_applications DROP COLUMN default_vat_percent;
