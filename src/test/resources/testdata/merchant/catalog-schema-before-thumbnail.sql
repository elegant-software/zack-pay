CREATE SCHEMA blitzpay;
CREATE TABLE blitzpay.merchant_products (
    id UUID PRIMARY KEY,
    merchant_id UUID NOT NULL,
    merchant_branch_id UUID,
    product_category_id UUID,
    product_code BIGINT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(2000),
    unit_price NUMERIC(12,4) NOT NULL,
    image_storage_key VARCHAR(1024),
    active BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
ALTER TABLE blitzpay.merchant_products ENABLE ROW LEVEL SECURITY;
ALTER TABLE blitzpay.merchant_products FORCE ROW LEVEL SECURITY;
CREATE POLICY merchant_tenant_isolation ON blitzpay.merchant_products
    USING (merchant_id = NULLIF(current_setting('app.current_merchant_id', true), '')::uuid);
CREATE ROLE catalog_reader NOLOGIN;
GRANT USAGE ON SCHEMA blitzpay TO catalog_reader;
GRANT SELECT, UPDATE ON blitzpay.merchant_products TO catalog_reader;
