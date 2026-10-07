-- Existing listings were created under the Australian-market contract.
ALTER TABLE properties ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'AUD';
ALTER TABLE properties ALTER COLUMN currency SET DEFAULT 'MNT';
ALTER TABLE properties ADD CONSTRAINT chk_property_currency CHECK (currency IN ('AUD', 'MNT'));

-- Preserve currency in retained responses and unpublished projections created before this migration.
UPDATE idempotency_records SET response_body = response_body || '{"currency":"AUD"}'::jsonb
WHERE response_body IS NOT NULL AND NOT (response_body ? 'currency');
UPDATE outbox_events SET payload = jsonb_set(payload, '{data,currency}', '"AUD"'::jsonb)
WHERE payload ? 'data' AND NOT ((payload->'data') ? 'currency');
