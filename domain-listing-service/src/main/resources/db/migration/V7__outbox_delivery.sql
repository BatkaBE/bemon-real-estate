ALTER TABLE outbox_events ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outbox_events ADD COLUMN next_attempt TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE TABLE featured_grants(id UUID PRIMARY KEY,property_id UUID NOT NULL REFERENCES properties(id),agent_id UUID NOT NULL,expires_at TIMESTAMPTZ NOT NULL,revoked_at TIMESTAMPTZ);
CREATE INDEX featured_property ON featured_grants(property_id,expires_at DESC) WHERE revoked_at IS NULL;
