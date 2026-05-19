-- liquibase formatted sql

-- changeset mohsenjd:20260519-001-add-service-category-duration
ALTER TABLE blitzpay.merchant_product_categories
    ADD COLUMN estimated_duration_minutes INT;

ALTER TABLE blitzpay.merchant_product_categories
    ADD CONSTRAINT chk_estimated_duration_positive
    CHECK (estimated_duration_minutes IS NULL OR (estimated_duration_minutes > 0 AND estimated_duration_minutes <= 480));

COMMENT ON COLUMN blitzpay.merchant_product_categories.estimated_duration_minutes IS 'Estimated service duration in minutes for appointment scheduling; optional, must be positive and <= 480 (8 hours) when present';

-- rollback ALTER TABLE blitzpay.merchant_product_categories DROP CONSTRAINT chk_estimated_duration_positive;
-- rollback ALTER TABLE blitzpay.merchant_product_categories DROP COLUMN estimated_duration_minutes;
