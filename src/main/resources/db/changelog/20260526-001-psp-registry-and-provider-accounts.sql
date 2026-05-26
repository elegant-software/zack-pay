-- liquibase formatted sql

-- changeset codex:20260526-001-psp-registry-and-provider-accounts-1
CREATE TABLE blitzpay.payment_service_providers (
    code         VARCHAR(32)  PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL
);

INSERT INTO blitzpay.payment_service_providers (code, display_name) VALUES
    ('STRIPE',    'Stripe'),
    ('TRUELAYER', 'TrueLayer'),
    ('ADYEN',     'Adyen'),
    ('PAYPAL',    'PayPal / Braintree');

-- rollback DROP TABLE blitzpay.payment_service_providers;

-- changeset codex:20260526-001-psp-registry-and-provider-accounts-2
CREATE TABLE blitzpay.merchant_provider_accounts (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_application_id UUID         NOT NULL REFERENCES blitzpay.merchant_applications(id),
    psp_code                VARCHAR(32)  NOT NULL REFERENCES blitzpay.payment_service_providers(code),
    enabled                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (merchant_application_id, psp_code)
);

CREATE INDEX idx_merchant_provider_accounts_merchant_id
    ON blitzpay.merchant_provider_accounts (merchant_application_id);

-- rollback DROP TABLE blitzpay.merchant_provider_accounts;

-- changeset codex:20260526-001-psp-registry-and-provider-accounts-3
CREATE TABLE blitzpay.merchant_stripe_provider_accounts (
    merchant_provider_account_id UUID         PRIMARY KEY REFERENCES blitzpay.merchant_provider_accounts(id),
    connected_account_id         VARCHAR(255) NOT NULL UNIQUE,
    charges_enabled              BOOLEAN      NOT NULL DEFAULT FALSE,
    details_submitted            BOOLEAN      NOT NULL DEFAULT FALSE,
    pay_by_bank_enabled          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- rollback DROP TABLE blitzpay.merchant_stripe_provider_accounts;

-- changeset codex:20260526-001-psp-registry-and-provider-accounts-4
-- Migrate merchant_application_payment_channels → merchant_provider_accounts
INSERT INTO blitzpay.merchant_provider_accounts (id, merchant_application_id, psp_code, enabled, created_at, updated_at)
SELECT
    gen_random_uuid(),
    merchant_application_id,
    payment_channel,
    TRUE,
    NOW(),
    NOW()
FROM blitzpay.merchant_application_payment_channels
ON CONFLICT (merchant_application_id, psp_code) DO NOTHING;

-- rollback -- no rollback for data migration

-- changeset codex:20260526-001-psp-registry-and-provider-accounts-5
-- Migrate merchant_stripe_connect → merchant_stripe_provider_accounts
-- First ensure a provider account row exists for every stripe connect record
INSERT INTO blitzpay.merchant_provider_accounts (id, merchant_application_id, psp_code, enabled, created_at, updated_at)
SELECT
    gen_random_uuid(),
    sc.merchant_id,
    'STRIPE',
    TRUE,
    sc.created_at,
    sc.updated_at
FROM blitzpay.merchant_stripe_connect sc
ON CONFLICT (merchant_application_id, psp_code) DO NOTHING;

INSERT INTO blitzpay.merchant_stripe_provider_accounts
    (merchant_provider_account_id, connected_account_id, charges_enabled, details_submitted, pay_by_bank_enabled, created_at, updated_at)
SELECT
    mpa.id,
    sc.connected_account_id,
    sc.charges_enabled,
    sc.details_submitted,
    sc.pay_by_bank_enabled,
    sc.created_at,
    sc.updated_at
FROM blitzpay.merchant_stripe_connect sc
JOIN blitzpay.merchant_provider_accounts mpa
    ON mpa.merchant_application_id = sc.merchant_id AND mpa.psp_code = 'STRIPE'
ON CONFLICT (merchant_provider_account_id) DO NOTHING;

-- rollback -- no rollback for data migration

-- changeset codex:20260526-001-psp-registry-and-provider-accounts-6
DROP TABLE blitzpay.merchant_application_payment_channels;
DROP TABLE blitzpay.merchant_branch_payment_channels;
DROP TABLE blitzpay.merchant_stripe_connect;

-- rollback -- tables cannot be recovered after drop without backup
