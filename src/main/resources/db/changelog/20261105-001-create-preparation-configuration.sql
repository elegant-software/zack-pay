--liquibase formatted sql
--changeset codex:20261105-001-create-preparation-configuration
CREATE TABLE blitzpay.merchant_preparation_configuration (
    branch_id UUID PRIMARY KEY REFERENCES blitzpay.merchant_branches(id) ON DELETE RESTRICT,
    configuration JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);
--rollback DROP TABLE blitzpay.merchant_preparation_configuration;
