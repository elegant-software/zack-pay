-- liquibase formatted sql

-- changeset codex:20260611-001-appearance-history
ALTER TABLE blitzpay.merchant_branch_appearances
    ADD COLUMN cancelled_at TIMESTAMPTZ;

CREATE INDEX ix_merchant_branch_appearances_cancelled_at
    ON blitzpay.merchant_branch_appearances (cancelled_at);

-- rollback DROP INDEX blitzpay.ix_merchant_branch_appearances_cancelled_at;
-- rollback ALTER TABLE blitzpay.merchant_branch_appearances DROP COLUMN cancelled_at;
