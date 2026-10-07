CREATE TABLE favorites (
    user_id UUID NOT NULL,
    property_id UUID NOT NULL REFERENCES properties(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id,property_id)
);
CREATE INDEX favorite_page ON favorites(user_id,created_at DESC,property_id DESC);
CREATE TABLE inquiries (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL REFERENCES properties(id),
    buyer_id UUID NOT NULL,
    agent_id UUID NOT NULL,
    buyer_name VARCHAR(120) NOT NULL,
    buyer_email VARCHAR(320) NOT NULL,
    buyer_phone VARCHAR(30) NOT NULL,
    message TEXT NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','CONTACTED','CLOSED')),
    reply TEXT NOT NULL DEFAULT '',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX inquiry_agent_page ON inquiries(agent_id,created_at DESC,id DESC);
CREATE INDEX inquiry_buyer_page ON inquiries(buyer_id,created_at DESC,id DESC);
CREATE TABLE notification_outbox (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ
);
