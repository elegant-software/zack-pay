-- liquibase formatted sql

-- changeset mohsenjd:20260522-002-create-merchant-branch-appearances
CREATE TABLE blitzpay.merchant_branch_appearances (
    id                  UUID             NOT NULL,
    branch_id           UUID             NOT NULL,
    declared_latitude   DOUBLE PRECISION NOT NULL,
    declared_longitude  DOUBLE PRECISION NOT NULL,
    live_latitude       DOUBLE PRECISION,
    live_longitude      DOUBLE PRECISION,
    start_time          TIMESTAMPTZ      NOT NULL,
    end_time            TIMESTAMPTZ      NOT NULL,
    name                VARCHAR(255),
    created_at          TIMESTAMPTZ      NOT NULL,
    updated_at          TIMESTAMPTZ      NOT NULL,
    CONSTRAINT pk_merchant_branch_appearances PRIMARY KEY (id),
    CONSTRAINT fk_appearances_branch FOREIGN KEY (branch_id)
        REFERENCES blitzpay.merchant_branches (id) ON DELETE CASCADE,
    CONSTRAINT chk_appearance_window CHECK (end_time > start_time)
);

CREATE INDEX idx_appearances_branch_id ON blitzpay.merchant_branch_appearances (branch_id);
CREATE INDEX idx_appearances_time_window ON blitzpay.merchant_branch_appearances (start_time, end_time);

COMMENT ON TABLE blitzpay.merchant_branch_appearances IS 'Scheduled time-windowed location commitments for MOBILE merchant branches';
COMMENT ON COLUMN blitzpay.merchant_branch_appearances.declared_latitude  IS 'Lat set at creation; used for discovery and as fallback when no live position is present';
COMMENT ON COLUMN blitzpay.merchant_branch_appearances.declared_longitude IS 'Lng set at creation; used for discovery and as fallback when no live position is present';
COMMENT ON COLUMN blitzpay.merchant_branch_appearances.live_latitude      IS 'Current GPS lat pushed by merchant app during operation; overrides declared location for proximity checks';
COMMENT ON COLUMN blitzpay.merchant_branch_appearances.live_longitude     IS 'Current GPS lng pushed by merchant app during operation; overrides declared location for proximity checks';

-- rollback DROP INDEX blitzpay.idx_appearances_time_window;
-- rollback DROP INDEX blitzpay.idx_appearances_branch_id;
-- rollback DROP TABLE blitzpay.merchant_branch_appearances;
