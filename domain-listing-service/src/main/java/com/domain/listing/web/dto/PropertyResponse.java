package com.domain.listing.web.dto;

import com.domain.listing.domain.model.ListingType;
import com.domain.listing.domain.model.Property;
import com.domain.listing.domain.model.PropertyStatus;
import com.domain.listing.domain.model.PropertyType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public representation of a property listing. */
public record PropertyResponse(
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
        PropertyAddressResponse address,
        PropertyStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    /** Maps a Listing aggregate without exposing its JPA shape to the HTTP API. */
    public static PropertyResponse from(final Property property) {
        return new PropertyResponse(
                property.getId(), property.getAgentId(), property.getTitle(),
                property.getPropertyType(), property.getListingType(), property.getPrice(),
                property.getBedrooms(), property.getBathrooms(), property.getParkingSpaces(),
                property.getLandSizeSqm(),
                new PropertyAddressResponse(property.getAddressLine(), property.getSuburb(),
                        property.getState(), property.getPostcode(), property.getLatitude(),
                        property.getLongitude()),
                property.getStatus(), property.getVersion(), property.getCreatedAt(), property.getUpdatedAt());
    }
}
