-- liquibase formatted sql

-- changeset mehdi:20260605-001-merchant-employee-tables
CREATE TABLE blitzpay.merchant_employees (
    id                  UUID         NOT NULL PRIMARY KEY,
    merchant_id         UUID         NOT NULL,
    name                VARCHAR(255) NOT NULL,
    status              VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ  NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_merchant_employees_merchant ON blitzpay.merchant_employees(merchant_id);

CREATE TABLE blitzpay.merchant_employee_branch_assignments (
    id          UUID        NOT NULL PRIMARY KEY,
    employee_id UUID        NOT NULL REFERENCES blitzpay.merchant_employees(id),
    branch_id   UUID        NOT NULL REFERENCES blitzpay.merchant_branches(id),
    created_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (employee_id, branch_id)
);

CREATE INDEX idx_emp_branch_assign_branch ON blitzpay.merchant_employee_branch_assignments(branch_id);

CREATE TABLE blitzpay.merchant_employee_availability (
    id          UUID        NOT NULL PRIMARY KEY,
    employee_id UUID        NOT NULL REFERENCES blitzpay.merchant_employees(id),
    branch_id   UUID        NOT NULL REFERENCES blitzpay.merchant_branches(id),
    day_of_week INT         NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_emp_availability_emp_branch ON blitzpay.merchant_employee_availability(employee_id, branch_id);

CREATE TABLE blitzpay.merchant_employee_availability_exceptions (
    id             UUID        NOT NULL PRIMARY KEY,
    employee_id    UUID        NOT NULL REFERENCES blitzpay.merchant_employees(id),
    branch_id      UUID        NOT NULL REFERENCES blitzpay.merchant_branches(id),
    exception_date DATE        NOT NULL,
    type           VARCHAR(32) NOT NULL,
    start_time     TIME,
    end_time       TIME,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_emp_avail_exc_emp_date ON blitzpay.merchant_employee_availability_exceptions(employee_id, exception_date);

CREATE TABLE blitzpay.merchant_employee_service_assignments (
    id                  UUID        NOT NULL PRIMARY KEY,
    employee_id         UUID        NOT NULL REFERENCES blitzpay.merchant_employees(id),
    product_category_id UUID        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL,
    UNIQUE (employee_id, product_category_id)
);

CREATE INDEX idx_emp_service_assign_category ON blitzpay.merchant_employee_service_assignments(product_category_id);

-- rollback DROP TABLE blitzpay.merchant_employee_service_assignments;
-- rollback DROP TABLE blitzpay.merchant_employee_availability_exceptions;
-- rollback DROP TABLE blitzpay.merchant_employee_availability;
-- rollback DROP TABLE blitzpay.merchant_employee_branch_assignments;
-- rollback DROP TABLE blitzpay.merchant_employees;
