-- liquibase formatted sql

-- changeset mehdi:20260617-001-shopper-profiles
CREATE TABLE blitzpay.shopper_profiles (
    id                  UUID          PRIMARY KEY,
    shopper_account_id  VARCHAR(512)  NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    street              VARCHAR(255)  NOT NULL,
    zip                 VARCHAR(64)   NOT NULL,
    city                VARCHAR(128)  NOT NULL,
    country             VARCHAR(64)   NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL
);

CREATE UNIQUE INDEX uq_shopper_profiles_account
    ON blitzpay.shopper_profiles (shopper_account_id);

-- rollback DROP TABLE blitzpay.shopper_profiles;
