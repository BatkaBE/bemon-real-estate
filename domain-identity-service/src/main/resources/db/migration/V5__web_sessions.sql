CREATE TABLE web_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES platform_users(id),
    payload TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    lease_id UUID,
    lease_until TIMESTAMPTZ
);
CREATE INDEX web_session_owner ON web_sessions(user_id);
