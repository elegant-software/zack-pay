-- liquibase formatted sql

-- changeset codex:20260606-001-merchant-user-assignments-1
CREATE TABLE blitzpay.merchant_user_assignments (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    keycloak_user_id        UUID        NOT NULL,
    merchant_application_id UUID        NOT NULL REFERENCES blitzpay.merchant_applications(id),
    branch_id               UUID        REFERENCES blitzpay.merchant_branches(id),
    role                    VARCHAR(32) NOT NULL,
    employee_id             UUID,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_merchant_branch UNIQUE (keycloak_user_id, merchant_application_id, branch_id)
);

CREATE INDEX idx_mua_keycloak_user_id ON blitzpay.merchant_user_assignments(keycloak_user_id);
CREATE INDEX idx_mua_merchant_id      ON blitzpay.merchant_user_assignments(merchant_application_id);

-- rollback DROP TABLE blitzpay.merchant_user_assignments;
