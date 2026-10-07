ALTER TABLE platform_users ADD COLUMN display_name VARCHAR(120) NOT NULL DEFAULT '';
ALTER TABLE platform_users ADD COLUMN phone VARCHAR(30) NOT NULL DEFAULT '';
ALTER TABLE platform_users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE platform_users ADD COLUMN auth_version BIGINT NOT NULL DEFAULT 0;
CREATE TABLE account_tokens (
    token_hash CHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES platform_users(id),
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('VERIFY', 'RESET')),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX account_token_owner ON account_tokens(user_id,purpose);
CREATE TABLE email_outbox (
    id UUID PRIMARY KEY,
    recipient VARCHAR(320) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX email_pending ON email_outbox(next_attempt_at) WHERE sent_at IS NULL;
CREATE TABLE request_buckets (
    bucket_key VARCHAR(180) PRIMARY KEY,
    window_start TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL
);
