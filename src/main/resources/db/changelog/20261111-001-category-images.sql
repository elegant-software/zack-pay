-- liquibase formatted sql

-- changeset blitzpay:20261111-001-category-images
ALTER TABLE blitzpay.merchant_product_categories ADD COLUMN image_storage_key VARCHAR(1024);
-- rollback ALTER TABLE blitzpay.merchant_product_categories DROP COLUMN image_storage_key;
