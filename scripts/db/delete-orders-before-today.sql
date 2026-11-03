-- Delete every order created BEFORE today (Europe/Berlin), keeping today's orders,
-- together with all rows in other tables that belong to those orders.
--
-- Usage:
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f scripts/db/delete-orders-before-today.sql
-- Ends with ROLLBACK (dry run). Change the last line to COMMIT to really delete.
--
-- Related tables (from src/main/resources/db/changelog):
--   FK on order_orders.id (RESTRICT / no action) -> must go first:
--     order_items, order_payment_attempts, order_invoices
--   No FK, linked by order_id_fk:
--     payment_settlements
--   No FK, linked by payment_request_id or order_id string (push notifications):
--     push_delivery_attempt, push_payment_status, push_device_registration
--   NOT deleted: appointments (order_orders.appointment_id points at them; they are
--   bookings in their own right, not owned by the order).

BEGIN;

-- Orders to delete
CREATE TEMP TABLE doomed_orders ON COMMIT DROP AS
SELECT id, order_id, last_payment_request_id
FROM blitzpay.order_orders
WHERE created_at < date_trunc('day', now() AT TIME ZONE 'Europe/Berlin') AT TIME ZONE 'Europe/Berlin';

-- Payment requests belonging to those orders
CREATE TEMP TABLE doomed_payment_requests ON COMMIT DROP AS
SELECT payment_request_id FROM blitzpay.order_payment_attempts
WHERE order_id_fk IN (SELECT id FROM doomed_orders)
UNION
SELECT last_payment_request_id FROM doomed_orders WHERE last_payment_request_id IS NOT NULL
UNION
SELECT payment_request_id FROM blitzpay.payment_settlements
WHERE order_id_fk IN (SELECT id FROM doomed_orders);

-- Summary (what will be deleted / kept)
SELECT 'order_orders (delete)' AS what, count(*) FROM doomed_orders
UNION ALL SELECT 'order_orders (keep, today)',
  count(*) FROM blitzpay.order_orders WHERE id NOT IN (SELECT id FROM doomed_orders)
UNION ALL SELECT 'order_items',
  count(*) FROM blitzpay.order_items WHERE order_id_fk IN (SELECT id FROM doomed_orders)
UNION ALL SELECT 'order_payment_attempts',
  count(*) FROM blitzpay.order_payment_attempts WHERE order_id_fk IN (SELECT id FROM doomed_orders)
UNION ALL SELECT 'order_invoices',
  count(*) FROM blitzpay.order_invoices WHERE order_id_fk IN (SELECT id FROM doomed_orders)
UNION ALL SELECT 'payment_settlements',
  count(*) FROM blitzpay.payment_settlements WHERE order_id_fk IN (SELECT id FROM doomed_orders)
UNION ALL SELECT 'push_delivery_attempt',
  count(*) FROM blitzpay.push_delivery_attempt
  WHERE payment_request_id IN (SELECT payment_request_id FROM doomed_payment_requests)
UNION ALL SELECT 'push_payment_status',
  count(*) FROM blitzpay.push_payment_status
  WHERE payment_request_id IN (SELECT payment_request_id FROM doomed_payment_requests)
     OR order_id IN (SELECT order_id FROM doomed_orders)
UNION ALL SELECT 'push_device_registration',
  count(*) FROM blitzpay.push_device_registration
  WHERE payment_request_id IN (SELECT payment_request_id FROM doomed_payment_requests)
     OR order_id IN (SELECT order_id FROM doomed_orders);

-- Push notification data
DELETE FROM blitzpay.push_delivery_attempt
WHERE payment_request_id IN (SELECT payment_request_id FROM doomed_payment_requests);

DELETE FROM blitzpay.push_payment_status
WHERE payment_request_id IN (SELECT payment_request_id FROM doomed_payment_requests)
   OR order_id IN (SELECT order_id FROM doomed_orders);

DELETE FROM blitzpay.push_device_registration
WHERE payment_request_id IN (SELECT payment_request_id FROM doomed_payment_requests)
   OR order_id IN (SELECT order_id FROM doomed_orders);

-- Order children
DELETE FROM blitzpay.payment_settlements    WHERE order_id_fk IN (SELECT id FROM doomed_orders);
DELETE FROM blitzpay.order_invoices         WHERE order_id_fk IN (SELECT id FROM doomed_orders);
DELETE FROM blitzpay.order_payment_attempts WHERE order_id_fk IN (SELECT id FROM doomed_orders);
DELETE FROM blitzpay.order_items            WHERE order_id_fk IN (SELECT id FROM doomed_orders);

-- Orders
DELETE FROM blitzpay.order_orders WHERE id IN (SELECT id FROM doomed_orders);

ROLLBACK;  -- change to COMMIT after checking the counts
