package com.domain.listing.application;

import com.domain.listing.domain.model.InvalidPropertyStatusTransitionException;
import com.domain.listing.domain.model.IdempotencyConflictException;
import com.domain.listing.domain.model.MediaType;
import com.domain.listing.domain.model.Property;
import com.domain.listing.domain.model.PropertyCursor;
import com.domain.listing.domain.model.PropertyDraft;
import com.domain.listing.domain.model.PropertyNotFoundException;
import com.domain.listing.domain.model.PropertyOwnershipException;
import com.domain.listing.domain.model.PropertyStatus;
import com.domain.listing.domain.model.PropertySearchCriteria;
import com.domain.listing.domain.model.StalePropertyVersionException;
import com.domain.listing.domain.port.PropertyRepository;
import com.domain.listing.domain.port.MediaStorage;
import com.domain.listing.domain.port.PropertyMediaRepository;
import com.domain.listing.domain.model.PropertyMedia;
import com.domain.listing.media.config.MediaProperties;
import com.domain.listing.persistence.IdempotencyRecord;
import com.domain.listing.persistence.JpaIdempotencyRecordRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application use cases for transactional property-listing mutations. */
@Service
public class PropertyApplicationService {
    private static final long MAX_IMAGE_UPLOAD_BYTES = 25L * 1024L * 1024L;
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final PropertyRepository propertyRepository;
    private final JpaIdempotencyRecordRepository idempotencyRecordRepository;
    private final PropertyMediaRepository propertyMediaRepository;
    private final MediaStorage mediaStorage;
    private final MediaProperties mediaProperties;
    private final Clock clock;

    /** Creates the service with domain-owned ports and time source. */
    public PropertyApplicationService(
            final PropertyRepository propertyRepository,
            final JpaIdempotencyRecordRepository idempotencyRecordRepository,
            final PropertyMediaRepository propertyMediaRepository,
            final MediaStorage mediaStorage,
            final MediaProperties mediaProperties,
            final Clock clock) {
        this.propertyRepository = propertyRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.propertyMediaRepository = propertyMediaRepository;
        this.mediaStorage = mediaStorage;
        this.mediaProperties = mediaProperties;
        this.clock = clock;
    }

    /** Creates a draft property owned by the authenticated agent. */
    @Transactional
    public Property create(final UUID agentId, final UUID idempotencyKey, final PropertyDraft draft) {
        final String requestHash = RequestFingerprint.forDraft(draft);
        final var existing = idempotencyRecordRepository.findById(idempotencyKey);
        if (existing.isPresent()) {
            final IdempotencyRecord record = existing.get();
            if (!record.matches(agentId, requestHash)) {
                throw new IdempotencyConflictException();
            }
            return find(record.getPropertyId());
        }

        final Property property = propertyRepository.save(Property.create(agentId, draft, clock));
        final Instant createdAt = Instant.now(clock);
        idempotencyRecordRepository.save(new IdempotencyRecord(
                idempotencyKey,
                agentId,
                requestHash,
                property.getId(),
                createdAt,
                createdAt.plus(Duration.ofHours(24))));
        return property;
    }

    /** Returns one listing or signals that it does not exist. */
    @Transactional(readOnly = true)
    public Property get(final UUID propertyId) {
        return find(propertyId);
    }

    /** Returns a stable keyset-paginated page of publicly discoverable listings. */
    @Transactional(readOnly = true)
    public PropertySearchPage search(
            final PropertySearchCriteria criteria,
            final String cursorValue,
            final int pageSize) {
        validateSearch(criteria, pageSize);
        final PropertyCursor cursor = cursorValue == null ? null : CursorCodec.decode(cursorValue);
        final List<Property> candidates = propertyRepository.findActive(criteria, cursor, pageSize + 1);
        final boolean hasMore = candidates.size() > pageSize;
        final List<Property> items = List.copyOf(candidates.subList(0, Math.min(candidates.size(), pageSize)));
        final String nextCursor = hasMore
                ? CursorCodec.encode(new PropertyCursor(
                        items.get(items.size() - 1).getCreatedAt(),
                        items.get(items.size() - 1).getId()))
                : null;
        return new PropertySearchPage(items, nextCursor, pageSize);
    }

    /** Creates one pending image record and returns a short-lived, direct S3 PUT capability. */
    @Transactional
    public MediaUploadAuthorization requestImageUpload(
            final UUID agentId,
            final UUID propertyId,
            final String contentType,
            final long contentLength,
            final short displayOrder) {
        owned(agentId, propertyId);
        final String extension = imageExtension(contentType, contentLength);
        final String objectKey = "properties/" + propertyId + "/" + UUID.randomUUID() + "." + extension;
        final Instant now = Instant.now(clock);
        final PropertyMedia media = propertyMediaRepository.save(new PropertyMedia(
                propertyId, MediaType.IMAGE, objectKey, displayOrder, now));
        return new MediaUploadAuthorization(
                media.getId(),
                mediaStorage.presignPut(objectKey, contentType, contentLength),
                now.plus(mediaProperties.presignDuration()));
    }

    /** Replaces editable attributes after ownership and version checks. */
    @Transactional
    public Property update(
            final UUID agentId,
            final UUID propertyId,
            final long expectedVersion,
            final PropertyDraft draft) {
        final Property property = ownedCurrentVersion(agentId, propertyId, expectedVersion);
        property.replace(draft, clock);
        return propertyRepository.save(property);
    }

    /** Performs a permitted lifecycle transition after ownership and version checks. */
    @Transactional
    public Property changeStatus(
            final UUID agentId,
            final UUID propertyId,
            final long expectedVersion,
            final PropertyStatus status) {
        final Property property = ownedCurrentVersion(agentId, propertyId, expectedVersion);
        property.transitionTo(status);
        return propertyRepository.save(property);
    }

    private Property ownedCurrentVersion(
            final UUID agentId,
            final UUID propertyId,
            final long expectedVersion) {
        final Property property = find(propertyId);
        verifyOwnership(property, agentId);
        if (!Long.valueOf(expectedVersion).equals(property.getVersion())) {
            throw new StalePropertyVersionException();
        }
        return property;
    }

    private Property find(final UUID propertyId) {
        return propertyRepository.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException(propertyId));
    }

    private Property owned(final UUID agentId, final UUID propertyId) {
        final Property property = find(propertyId);
        verifyOwnership(property, agentId);
        return property;
    }

    private void verifyOwnership(final Property property, final UUID agentId) {
        if (!property.isOwnedBy(agentId)) {
            throw new PropertyOwnershipException();
        }
    }

    private void validateSearch(final PropertySearchCriteria criteria, final int pageSize) {
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize must be between 1 and 100");
        }
        if (criteria.minPrice() != null && criteria.maxPrice() != null
                && criteria.minPrice().compareTo(criteria.maxPrice()) > 0) {
            throw new IllegalArgumentException("minPrice must not exceed maxPrice");
        }
    }

    private String imageExtension(final String contentType, final long contentLength) {
        final String extension = IMAGE_EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("contentType must be image/jpeg, image/png, or image/webp");
        }
        if (contentLength < 1 || contentLength > MAX_IMAGE_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Image contentLength must be between 1 and 26214400 bytes");
        }
        return extension;
    }
}
