-- Retain verified pre-registry receipts so upgrades cannot reuse them for a later order.
INSERT INTO provider_receipts(receipt_id,order_id)
SELECT receipt,orders.id FROM payment_orders orders
CROSS JOIN LATERAL regexp_split_to_table(orders.receipt_id,',') AS receipt
WHERE orders.receipt_id IS NOT NULL;
