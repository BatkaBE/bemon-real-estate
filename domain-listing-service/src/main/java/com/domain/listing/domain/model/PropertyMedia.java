package com.domain.listing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Metadata for a media object attached to a property; binary content remains in object storage. */
@Entity
@Table(name = "property_media")
public class PropertyMedia {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    private MediaType mediaType;

    @Column(name = "object_key", nullable = false, updatable = false)
    private String objectKey;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "upload_status", nullable = false, length = 20)
    private MediaUploadStatus uploadStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected PropertyMedia() {
    }

    /** Creates a pending media record before direct object-storage upload begins. */
    public PropertyMedia(
            final UUID propertyId,
            final MediaType mediaType,
            final String objectKey,
            final short displayOrder,
            final Instant createdAt) {
        this.propertyId = propertyId;
        this.mediaType = mediaType;
        this.objectKey = objectKey;
        this.displayOrder = displayOrder;
        this.uploadStatus = MediaUploadStatus.PENDING;
        this.createdAt = createdAt;
    }

    /** @return persistent media identifier. */
    public UUID getId() { return id; }
    /** @return object-storage key, not a public URL. */
    public String getObjectKey() { return objectKey; }
}
