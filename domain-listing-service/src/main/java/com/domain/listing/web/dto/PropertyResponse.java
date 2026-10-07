package com.domain.listing.web.dto;

import com.domain.listing.domain.model.ListingType;
import com.domain.listing.domain.model.Property;
import com.domain.listing.domain.model.PropertySnapshot;
import com.domain.listing.domain.model.PropertyStatus;
import com.domain.listing.domain.model.PropertyType;
import com.domain.listing.domain.model.PriceCurrency;
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
        Instant updatedAt,
        PriceCurrency currency) {

    /** Maps a Listing aggregate without exposing its JPA shape to the HTTP API. */
    public static PropertyResponse from(final Property property) {
        return from(PropertySnapshot.from(property));
    }

    /** Maps an immutable create snapshot without changing the response on a retry. */
    public static PropertyResponse from(final PropertySnapshot property) {
        return new PropertyResponse(
                property.id(), property.agentId(), property.title(),
                property.propertyType(), property.listingType(), property.price(),
                property.bedrooms(), property.bathrooms(), property.parkingSpaces(),
                property.landSizeSqm(),
                new PropertyAddressResponse(property.address().addressLine(), property.address().suburb(),
                        property.address().state(), property.address().postcode(), property.address().latitude(),
                        property.address().longitude()),
                property.status(), property.version(), property.createdAt(), property.updatedAt(), property.currency());
    }
}
