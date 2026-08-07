ALTER TABLE property_media
    RENAME COLUMN url TO object_key;

ALTER TABLE property_media
    ADD COLUMN upload_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE property_media
    ADD CONSTRAINT chk_property_media_upload_status
    CHECK (upload_status IN ('PENDING', 'UPLOADED', 'FAILED'));

CREATE INDEX idx_property_media_pending_uploads
    ON property_media (created_at)
    WHERE upload_status = 'PENDING';
