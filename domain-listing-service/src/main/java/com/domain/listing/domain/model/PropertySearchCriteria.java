package com.domain.listing.domain.model;

import java.math.BigDecimal;

/** Optional filters supported by the public listing browse endpoint. */
public record PropertySearchCriteria(
        String suburb,
        ListingType listingType,
        PropertyType propertyType,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minBedrooms) {
}
