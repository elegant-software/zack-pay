-- liquibase formatted sql

-- changeset mohsenjd:20260522-001-add-merchant-mobility
ALTER TABLE blitzpay.merchant_applications
    ADD COLUMN merchant_mobility VARCHAR(32) NOT NULL DEFAULT 'BRICK_AND_MORTAR';

ALTER TABLE blitzpay.merchant_applications
    ADD CONSTRAINT chk_merchant_mobility
    CHECK (merchant_mobility IN ('BRICK_AND_MORTAR', 'MOBILE'));

COMMENT ON COLUMN blitzpay.merchant_applications.merchant_mobility IS 'Operational mode of the merchant: BRICK_AND_MORTAR for fixed-location merchants, MOBILE for merchants operating from scheduled Appearances';

-- rollback ALTER TABLE blitzpay.merchant_applications DROP CONSTRAINT chk_merchant_mobility;
-- rollback ALTER TABLE blitzpay.merchant_applications DROP COLUMN merchant_mobility;
