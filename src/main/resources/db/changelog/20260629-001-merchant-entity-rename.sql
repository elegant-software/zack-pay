-- liquibase formatted sql

-- changeset mehdi:20260629-001-create-merchants-table
-- Create the new merchants table with all relevant columns from merchant_applications
CREATE TABLE blitzpay.merchants (
    id                      UUID            NOT NULL,
    merchant_code           VARCHAR(64)     NOT NULL,
    merchant_name           VARCHAR(255)    NOT NULL,
    merchant_status         VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    business_type           VARCHAR(64)     NOT NULL,
    operating_country       VARCHAR(8)      NOT NULL,
    logo_storage_key        VARCHAR(255),
    website                 VARCHAR(512),
    address_line1           VARCHAR(255),
    address_line2           VARCHAR(255),
    city                    VARCHAR(255),
    postal_code             VARCHAR(64),
    country                 VARCHAR(2),
    contact_email_public    VARCHAR(255),
    contact_phone_public    VARCHAR(64),
    latitude                DOUBLE PRECISION,
    longitude               DOUBLE PRECISION,
    geofence_radius_m       INTEGER,
    google_place_id         VARCHAR(255),
    place_formatted_address VARCHAR(1024),
    place_rating            DOUBLE PRECISION,
    place_review_count      INTEGER,
    place_enrichment_status VARCHAR(32),
    place_enrichment_error  VARCHAR(1024),
    place_enriched_at       TIMESTAMPTZ,
    merchant_mobility       VARCHAR(32)     NOT NULL DEFAULT 'BRICK_AND_MORTAR',
    booking_interval_minutes   INT,
    booking_lead_time_minutes  INT,
    booking_horizon_days       INT,
    reminder_lead_time_minutes INT,
    default_vat_percent     NUMERIC(5,2),
    x_handle                VARCHAR(128),
    instagram_handle        VARCHAR(128),
    created_at              TIMESTAMPTZ     NOT NULL,
    last_updated_at         TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_merchants PRIMARY KEY (id),
    CONSTRAINT uk_merchants_merchant_code UNIQUE (merchant_code),
    CONSTRAINT chk_merchants_mobility CHECK (merchant_mobility IN ('BRICK_AND_MORTAR', 'MOBILE'))
);

-- rollback DROP TABLE blitzpay.merchants;

-- changeset mehdi:20260629-002-copy-merchant-data
-- Copy data from merchant_applications to merchants
INSERT INTO blitzpay.merchants (
    id, merchant_code, merchant_name, merchant_status,
    business_type, operating_country, logo_storage_key, website,
    address_line1, address_line2, city, postal_code, country,
    contact_email_public, contact_phone_public,
    latitude, longitude, geofence_radius_m, google_place_id,
    place_formatted_address, place_rating, place_review_count,
    place_enrichment_status, place_enrichment_error, place_enriched_at,
    merchant_mobility,
    booking_interval_minutes, booking_lead_time_minutes,
    booking_horizon_days, reminder_lead_time_minutes,
    default_vat_percent,
    created_at, last_updated_at
)
SELECT
    id,
    merchant_code,
    merchant_name,
    merchant_status,
    business_type,
    operating_country,
    logo_storage_key,
    website,
    address_line1, address_line2, city, postal_code, country,
    contact_email_public, contact_phone_public,
    latitude, longitude, geofence_radius_m, google_place_id,
    place_formatted_address, place_rating, place_review_count,
    place_enrichment_status, place_enrichment_error, place_enriched_at,
    merchant_mobility,
    booking_interval_minutes, booking_lead_time_minutes,
    booking_horizon_days, reminder_lead_time_minutes,
    default_vat_percent,
    created_at, last_updated_at
FROM blitzpay.merchant_applications;

-- rollback DELETE FROM blitzpay.merchants;

-- changeset mehdi:20260629-003-migrate-merchant-branches
-- Update merchant_branches to point to merchants table
ALTER TABLE blitzpay.merchant_branches
    DROP CONSTRAINT IF EXISTS fk_merchant_branches_application,
    DROP CONSTRAINT IF EXISTS fk_merchant_branches_application_restrict;
DROP INDEX IF EXISTS blitzpay.idx_merchant_branches_merchant_application_id;
DROP INDEX IF EXISTS blitzpay.idx_merchant_branches_merchant_id;
ALTER TABLE blitzpay.merchant_branches RENAME COLUMN merchant_application_id TO merchant_id;
ALTER TABLE blitzpay.merchant_branches
    ADD CONSTRAINT fk_merchant_branches_merchant
        FOREIGN KEY (merchant_id) REFERENCES blitzpay.merchants(id) ON DELETE RESTRICT;
CREATE INDEX idx_merchant_branches_merchant_id ON blitzpay.merchant_branches (merchant_id);
ALTER TABLE blitzpay.merchant_branches
    DROP CONSTRAINT IF EXISTS uk_merchant_branches_code_per_merchant;
ALTER TABLE blitzpay.merchant_branches
    ADD CONSTRAINT uk_merchant_branches_code_per_merchant UNIQUE (merchant_id, branch_code);

-- rollback ALTER TABLE blitzpay.merchant_branches DROP CONSTRAINT uk_merchant_branches_code_per_merchant;
-- rollback DROP INDEX blitzpay.idx_merchant_branches_merchant_id;
-- rollback ALTER TABLE blitzpay.merchant_branches DROP CONSTRAINT fk_merchant_branches_merchant;
-- rollback ALTER TABLE blitzpay.merchant_branches RENAME COLUMN merchant_id TO merchant_application_id;

-- changeset mehdi:20260629-004-migrate-merchant-products
-- Update merchant_products to point to merchants table
DROP POLICY IF EXISTS merchant_tenant_isolation ON blitzpay.merchant_products;
ALTER TABLE blitzpay.merchant_products
    DROP CONSTRAINT IF EXISTS fk_merchant_products_application;
DROP INDEX IF EXISTS blitzpay.ix_merchant_products_merchant;
DROP INDEX IF EXISTS blitzpay.ix_merchant_products_active;
ALTER TABLE blitzpay.merchant_products RENAME COLUMN merchant_application_id TO merchant_id;
ALTER TABLE blitzpay.merchant_products
    ADD CONSTRAINT fk_merchant_products_merchant
        FOREIGN KEY (merchant_id) REFERENCES blitzpay.merchants(id);
CREATE INDEX ix_merchant_products_merchant ON blitzpay.merchant_products (merchant_id);
CREATE INDEX ix_merchant_products_active ON blitzpay.merchant_products (merchant_id, active);
CREATE POLICY merchant_tenant_isolation
    ON blitzpay.merchant_products
    USING (
        merchant_id = NULLIF(current_setting('app.current_merchant_id', true), '')::uuid
    );

-- rollback DROP POLICY merchant_tenant_isolation ON blitzpay.merchant_products;
-- rollback DROP INDEX blitzpay.ix_merchant_products_active;
-- rollback DROP INDEX blitzpay.ix_merchant_products_merchant;
-- rollback ALTER TABLE blitzpay.merchant_products DROP CONSTRAINT fk_merchant_products_merchant;
-- rollback ALTER TABLE blitzpay.merchant_products RENAME COLUMN merchant_id TO merchant_application_id;

-- changeset mehdi:20260629-005-migrate-merchant-product-categories
-- Update merchant_product_categories to point to merchants table
DROP POLICY IF EXISTS merchant_tenant_isolation ON blitzpay.merchant_product_categories;
ALTER TABLE blitzpay.merchant_product_categories
    DROP CONSTRAINT IF EXISTS fk_merchant_product_categories_application;
DROP INDEX IF EXISTS blitzpay.uq_merchant_product_category_name;
DROP INDEX IF EXISTS blitzpay.ix_merchant_product_categories_merchant;
ALTER TABLE blitzpay.merchant_product_categories RENAME COLUMN merchant_application_id TO merchant_id;
ALTER TABLE blitzpay.merchant_product_categories
    ADD CONSTRAINT fk_merchant_product_categories_merchant
        FOREIGN KEY (merchant_id) REFERENCES blitzpay.merchants(id);
CREATE UNIQUE INDEX uq_merchant_product_category_name
    ON blitzpay.merchant_product_categories (merchant_id, lower(name));
CREATE INDEX ix_merchant_product_categories_merchant
    ON blitzpay.merchant_product_categories (merchant_id);
CREATE POLICY merchant_tenant_isolation
    ON blitzpay.merchant_product_categories
    USING (
        merchant_id = NULLIF(current_setting('app.current_merchant_id', true), '')::uuid
    );

-- rollback DROP POLICY merchant_tenant_isolation ON blitzpay.merchant_product_categories;
-- rollback DROP INDEX blitzpay.ix_merchant_product_categories_merchant;
-- rollback DROP INDEX blitzpay.uq_merchant_product_category_name;
-- rollback ALTER TABLE blitzpay.merchant_product_categories DROP CONSTRAINT fk_merchant_product_categories_merchant;
-- rollback ALTER TABLE blitzpay.merchant_product_categories RENAME COLUMN merchant_id TO merchant_application_id;

-- changeset mehdi:20260629-006-migrate-merchant-application-offerings
-- Update merchant_application_offerings to point to merchants table
ALTER TABLE blitzpay.merchant_application_offerings
    DROP CONSTRAINT IF EXISTS pk_merchant_application_offerings;
ALTER TABLE blitzpay.merchant_application_offerings RENAME COLUMN merchant_application_id TO merchant_id;
ALTER TABLE blitzpay.merchant_application_offerings
    ADD CONSTRAINT pk_merchant_application_offerings PRIMARY KEY (merchant_id, offering_code),
    ADD CONSTRAINT fk_merchant_offering_assignments_merchant
        FOREIGN KEY (merchant_id) REFERENCES blitzpay.merchants(id);

-- rollback ALTER TABLE blitzpay.merchant_application_offerings DROP CONSTRAINT fk_merchant_offering_assignments_merchant;
-- rollback ALTER TABLE blitzpay.merchant_application_offerings DROP CONSTRAINT pk_merchant_application_offerings;
-- rollback ALTER TABLE blitzpay.merchant_application_offerings RENAME COLUMN merchant_id TO merchant_application_id;

-- changeset mehdi:20260629-007-migrate-merchant-provider-accounts
-- Update merchant_provider_accounts to point to merchants table
ALTER TABLE blitzpay.merchant_provider_accounts
    DROP CONSTRAINT IF EXISTS merchant_provider_accounts_merchant_application_id_psp_code_key;
ALTER TABLE blitzpay.merchant_provider_accounts
    DROP CONSTRAINT IF EXISTS merchant_provider_accounts_merchant_application_id_fkey;
DROP INDEX IF EXISTS blitzpay.idx_merchant_provider_accounts_merchant_id;
ALTER TABLE blitzpay.merchant_provider_accounts RENAME COLUMN merchant_application_id TO merchant_id;
ALTER TABLE blitzpay.merchant_provider_accounts
    ADD CONSTRAINT fk_merchant_provider_accounts_merchant
        FOREIGN KEY (merchant_id) REFERENCES blitzpay.merchants(id),
    ADD UNIQUE (merchant_id, psp_code);
CREATE INDEX idx_merchant_provider_accounts_merchant_id
    ON blitzpay.merchant_provider_accounts (merchant_id);

-- rollback DROP INDEX blitzpay.idx_merchant_provider_accounts_merchant_id;
-- rollback ALTER TABLE blitzpay.merchant_provider_accounts DROP CONSTRAINT fk_merchant_provider_accounts_merchant;
-- rollback ALTER TABLE blitzpay.merchant_provider_accounts RENAME COLUMN merchant_id TO merchant_application_id;

-- changeset mehdi:20260629-008-migrate-merchant-user-assignments
-- Update merchant_user_assignments to point to merchants table
ALTER TABLE blitzpay.merchant_user_assignments
    DROP CONSTRAINT IF EXISTS uq_user_merchant_branch;
ALTER TABLE blitzpay.merchant_user_assignments
    DROP CONSTRAINT IF EXISTS merchant_user_assignments_merchant_application_id_fkey;
DROP INDEX IF EXISTS blitzpay.idx_mua_merchant_id;
ALTER TABLE blitzpay.merchant_user_assignments RENAME COLUMN merchant_application_id TO merchant_id;
ALTER TABLE blitzpay.merchant_user_assignments
    ADD CONSTRAINT fk_merchant_user_assignments_merchant
        FOREIGN KEY (merchant_id) REFERENCES blitzpay.merchants(id),
    ADD CONSTRAINT uq_user_merchant_branch UNIQUE (keycloak_user_id, merchant_id, branch_id);
CREATE INDEX idx_mua_merchant_id ON blitzpay.merchant_user_assignments(merchant_id);

-- rollback DROP INDEX blitzpay.idx_mua_merchant_id;
-- rollback ALTER TABLE blitzpay.merchant_user_assignments DROP CONSTRAINT uq_user_merchant_branch;
-- rollback ALTER TABLE blitzpay.merchant_user_assignments DROP CONSTRAINT fk_merchant_user_assignments_merchant;
-- rollback ALTER TABLE blitzpay.merchant_user_assignments RENAME COLUMN merchant_id TO merchant_application_id;

-- changeset mehdi:20260629-009-drop-old-merchant-tables
-- Drop old merchant onboarding tables that are no longer needed
ALTER TABLE blitzpay.merchant_applications DROP CONSTRAINT IF EXISTS fk_merchant_applications_monitoring;
DROP TABLE IF EXISTS blitzpay.merchant_review_decisions;
DROP TABLE IF EXISTS blitzpay.merchant_people;
DROP TABLE IF EXISTS blitzpay.merchant_supporting_materials;
DROP TABLE IF EXISTS blitzpay.merchant_applications;
DROP TABLE IF EXISTS blitzpay.merchant_monitoring_records;

-- rollback -- cannot recover dropped tables without backup

-- changeset mehdi:20260629-010-merchant-location-index
CREATE INDEX ix_merchants_location
    ON blitzpay.merchants (latitude, longitude)
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

-- rollback DROP INDEX blitzpay.ix_merchants_location;
