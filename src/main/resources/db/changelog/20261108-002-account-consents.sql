-- liquibase formatted sql

-- changeset mehdi:20261108-002-account-consents
-- GDPR Art. 7(1): proof that an account accepted the Terms, Privacy Policy and DPA, and which version.
-- One row per acceptance; rows are deleted with the account.
CREATE TABLE blitzpay.account_consents (
    id               UUID         PRIMARY KEY,
    keycloak_user_id UUID         NOT NULL,
    terms_version    VARCHAR(32)  NOT NULL,
    accepted_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_account_consents_user ON blitzpay.account_consents (keycloak_user_id);
-- rollback DROP TABLE blitzpay.account_consents;
