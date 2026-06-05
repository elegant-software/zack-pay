-- liquibase formatted sql

-- changeset mehdi:20260605-002-merchant-branch-opening-hours
ALTER TABLE blitzpay.merchant_branches
    ADD COLUMN timezone VARCHAR(64) NULL;

CREATE TABLE blitzpay.merchant_branch_opening_hours (
    id          UUID        NOT NULL PRIMARY KEY,
    branch_id   UUID        NOT NULL REFERENCES blitzpay.merchant_branches(id),
    day_of_week INT         NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_branch_opening_hours_branch ON blitzpay.merchant_branch_opening_hours(branch_id);

CREATE TABLE blitzpay.merchant_branch_opening_exceptions (
    id             UUID        NOT NULL PRIMARY KEY,
    branch_id      UUID        NOT NULL REFERENCES blitzpay.merchant_branches(id),
    exception_date DATE        NOT NULL,
    type           VARCHAR(32) NOT NULL,
    start_time     TIME,
    end_time       TIME,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_branch_opening_exc_branch_date ON blitzpay.merchant_branch_opening_exceptions(branch_id, exception_date);

-- rollback DROP TABLE blitzpay.merchant_branch_opening_exceptions;
-- rollback DROP TABLE blitzpay.merchant_branch_opening_hours;
-- rollback ALTER TABLE blitzpay.merchant_branches DROP COLUMN timezone;
