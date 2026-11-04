-- liquibase formatted sql

-- changeset mehdi:20261104-001-merchant-floor-plans
-- A named picture of a Branch's room or area ("Inside", "Terrace") on which staff place Tables.
CREATE TABLE blitzpay.merchant_floor_plans (
    id                UUID         NOT NULL,
    branch_id         UUID         NOT NULL,
    name              VARCHAR(100) NOT NULL,
    image_storage_key VARCHAR(512),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_merchant_floor_plans PRIMARY KEY (id),
    CONSTRAINT fk_merchant_floor_plans_merchant_branches
        FOREIGN KEY (branch_id) REFERENCES blitzpay.merchant_branches (id) ON DELETE RESTRICT
);
CREATE INDEX ix_merchant_floor_plans_branch_id ON blitzpay.merchant_floor_plans (branch_id);
-- rollback DROP TABLE blitzpay.merchant_floor_plans;

-- changeset mehdi:20261104-001-merchant-tables
-- A seating place within a Branch. `code` is the opaque, permanent Table Code carried by the Table Link
-- (ADR-0023); `x`/`y` are the Table Placement as fractions of the Floor Plan picture. `guest_count` is the
-- Guest Count of the current sitting, set at `guests_set_at`; clearing the Table resets both.
CREATE TABLE blitzpay.merchant_tables (
    id            UUID             NOT NULL,
    branch_id     UUID             NOT NULL,
    code          VARCHAR(6)       NOT NULL,
    name          VARCHAR(100)     NOT NULL,
    active        BOOLEAN          NOT NULL DEFAULT TRUE,
    floor_plan_id UUID,
    x             DOUBLE PRECISION,
    y             DOUBLE PRECISION,
    seats         INTEGER,
    guest_count   INTEGER,
    guests_set_at TIMESTAMPTZ,
    cleared_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ      NOT NULL DEFAULT now(),
    CONSTRAINT pk_merchant_tables PRIMARY KEY (id),
    CONSTRAINT fk_merchant_tables_merchant_branches
        FOREIGN KEY (branch_id) REFERENCES blitzpay.merchant_branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_merchant_tables_merchant_floor_plans
        FOREIGN KEY (floor_plan_id) REFERENCES blitzpay.merchant_floor_plans (id) ON DELETE RESTRICT,
    CONSTRAINT ck_merchant_tables_seats CHECK (seats IS NULL OR seats BETWEEN 1 AND 50),
    CONSTRAINT ck_merchant_tables_guest_count CHECK (guest_count IS NULL OR guest_count BETWEEN 1 AND 50),
    CONSTRAINT ck_merchant_tables_placement CHECK (
        (floor_plan_id IS NULL AND x IS NULL AND y IS NULL)
        OR (floor_plan_id IS NOT NULL AND x BETWEEN 0 AND 1 AND y BETWEEN 0 AND 1)
    )
);
CREATE UNIQUE INDEX ux_merchant_tables_code ON blitzpay.merchant_tables (code);
CREATE INDEX ix_merchant_tables_branch_id ON blitzpay.merchant_tables (branch_id);
CREATE INDEX ix_merchant_tables_floor_plan_id ON blitzpay.merchant_tables (floor_plan_id);
-- rollback DROP TABLE blitzpay.merchant_tables;

-- changeset mehdi:20261104-001-merchant-branches-capacity
-- Branch Capacity: the maximum number of guests a Branch accepts at the same time, set by the merchant. Optional;
-- never blocks ordering (a Reservation may not exceed it).
ALTER TABLE blitzpay.merchant_branches
    ADD COLUMN capacity INTEGER,
    ADD CONSTRAINT ck_merchant_branches_capacity CHECK (capacity IS NULL OR capacity > 0);
-- rollback ALTER TABLE blitzpay.merchant_branches DROP CONSTRAINT ck_merchant_branches_capacity, DROP COLUMN capacity;
