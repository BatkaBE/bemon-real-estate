ALTER TABLE idempotency_records
    ADD COLUMN property_id UUID REFERENCES properties(id);

ALTER TABLE idempotency_records
    ALTER COLUMN property_id SET NOT NULL;
