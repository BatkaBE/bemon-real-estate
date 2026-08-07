CREATE TABLE properties (
    id UUID PRIMARY KEY,
    agent_id UUID NOT NULL,
    title VARCHAR(255) NOT NULL,
    property_type VARCHAR(50) NOT NULL,
    listing_type VARCHAR(20) NOT NULL,
    price NUMERIC(12, 2),
    bedrooms SMALLINT,
    bathrooms SMALLINT,
    parking_spaces SMALLINT,
    land_size_sqm NUMERIC(10, 2),
    address_line VARCHAR(255) NOT NULL,
    suburb VARCHAR(100) NOT NULL,
    state VARCHAR(10) NOT NULL,
    postcode VARCHAR(10) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_properties_price_non_negative CHECK (price IS NULL OR price >= 0),
    CONSTRAINT chk_properties_coordinates CHECK (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_properties_public_browse
    ON properties (status, listing_type, suburb, created_at DESC);

CREATE INDEX idx_properties_agent_id ON properties (agent_id);

CREATE TABLE property_media (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    media_type VARCHAR(20) NOT NULL,
    url TEXT NOT NULL,
    display_order SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_property_media_display_order UNIQUE (property_id, display_order)
);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_unpublished ON outbox_events (occurred_at) WHERE published_at IS NULL;

CREATE TABLE idempotency_records (
    idempotency_key UUID PRIMARY KEY,
    agent_id UUID NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_status SMALLINT,
    response_body JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL
);
