-- liquibase formatted sql

-- changeset mehdi:20261102-001-order-invoices-skip-historical
-- Invoices are now emailed by InvoiceBatchJob. Mark every invoice generated before the job existed
-- as SKIPPED so its first run does not email historical invoices.
UPDATE blitzpay.order_invoices SET delivery_status = 'SKIPPED' WHERE delivery_status = 'PENDING';

-- rollback SELECT 1; -- data migration, no safe rollback (SKIPPED rows cannot be told apart from genuine skips)
