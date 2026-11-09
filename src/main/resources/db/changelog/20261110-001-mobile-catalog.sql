-- liquibase formatted sql

-- changeset mohamadihamit:20261110-001-mobile-catalog
ALTER TABLE blitzpay.merchant_products ADD COLUMN thumbnail_storage_key VARCHAR(1024);
CREATE INDEX ix_merchant_products_catalog_page
    ON blitzpay.merchant_products (merchant_id, merchant_branch_id, created_at, id);
CREATE INDEX ix_merchant_products_catalog_category_page
    ON blitzpay.merchant_products (merchant_id, merchant_branch_id, product_category_id, created_at, id);
-- rollback DROP INDEX blitzpay.ix_merchant_products_catalog_category_page;
-- rollback DROP INDEX blitzpay.ix_merchant_products_catalog_page;
-- rollback ALTER TABLE blitzpay.merchant_products DROP COLUMN thumbnail_storage_key;
