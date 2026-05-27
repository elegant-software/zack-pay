--liquibase formatted sql

--changeset blitzpay:20260527-001-merchant-adyen-provider-accounts
CREATE TABLE blitzpay.merchant_adyen_provider_accounts (
    merchant_provider_account_id UUID NOT NULL,
    legal_entity_id              VARCHAR(64)  NOT NULL,
    account_holder_id            VARCHAR(64)  NOT NULL,
    balance_account_id           VARCHAR(64)  NOT NULL,
    receive_payments_enabled     BOOLEAN      NOT NULL DEFAULT FALSE,
    details_submitted            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at                   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at                   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_merchant_adyen_provider_accounts
        PRIMARY KEY (merchant_provider_account_id),
    CONSTRAINT fk_merchant_adyen_provider_accounts_provider_account
        FOREIGN KEY (merchant_provider_account_id)
        REFERENCES blitzpay.merchant_provider_accounts (id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ux_merchant_adyen_provider_accounts_legal_entity
    ON blitzpay.merchant_adyen_provider_accounts (legal_entity_id);

CREATE UNIQUE INDEX ux_merchant_adyen_provider_accounts_balance_account
    ON blitzpay.merchant_adyen_provider_accounts (balance_account_id);

--rollback DROP TABLE blitzpay.merchant_adyen_provider_accounts;
