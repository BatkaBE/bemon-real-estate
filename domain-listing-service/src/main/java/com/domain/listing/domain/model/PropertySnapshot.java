package com.domain.listing.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable listing state retained for idempotent responses and outbox projections. */
public record PropertySnapshot(
        UUID id,
        UUID agentId,
        String title,
        PropertyType propertyType,
        ListingType listingType,
        BigDecimal price,
        Integer bedrooms,
        Integer bathrooms,
        Integer parkingSpaces,
        BigDecimal landSizeSqm,
        PropertyAddress address,
        PropertyStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        PriceCurrency currency) {

    /** Captures the persisted aggregate's state and optimistic-lock version. */
    public static PropertySnapshot from(final Property property) {
        return new PropertySnapshot(
                property.getId(), property.getAgentId(), property.getTitle(),
                property.getPropertyType(), property.getListingType(), property.getPrice(),
                property.getBedrooms(), property.getBathrooms(), property.getParkingSpaces(),
                property.getLandSizeSqm(),
                new PropertyAddress(property.getAddressLine(), property.getSuburb(),
                        property.getState(), property.getPostcode(),
                        property.getLatitude(), property.getLongitude()),
                property.getStatus(), property.getVersion(), property.getCreatedAt(), property.getUpdatedAt(),
                property.getCurrency());
    }
}
