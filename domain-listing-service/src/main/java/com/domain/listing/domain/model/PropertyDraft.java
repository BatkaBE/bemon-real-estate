package com.domain.listing.domain.model;

import java.math.BigDecimal;

/** Validated editable attributes for creating or updating a listing. */
public record PropertyDraft(
        String title,
        PropertyType propertyType,
        ListingType listingType,
        BigDecimal price,
        Integer bedrooms,
        Integer bathrooms,
        Integer parkingSpaces,
        BigDecimal landSizeSqm,
        PropertyAddress address) {
}
