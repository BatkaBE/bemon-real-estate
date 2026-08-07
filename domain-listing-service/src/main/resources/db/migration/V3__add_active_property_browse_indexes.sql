CREATE INDEX idx_properties_active_browse_cursor
    ON properties (created_at DESC, id DESC)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_properties_active_suburb_browse_cursor
    ON properties (suburb, created_at DESC, id DESC)
    WHERE status = 'ACTIVE';
