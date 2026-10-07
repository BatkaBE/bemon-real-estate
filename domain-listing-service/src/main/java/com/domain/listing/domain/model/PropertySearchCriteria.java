package com.domain.listing.domain.model;

import java.math.BigDecimal;

/** Optional filters supported by the public listing browse endpoint. */
public record PropertySearchCriteria(
        String suburb,
        ListingType listingType,
        PropertyType propertyType,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer minBedrooms,
        PriceCurrency currency) {
    /** Keeps the public browse call signature for clients without a currency filter. */
    public PropertySearchCriteria(final String suburb, final ListingType listingType, final PropertyType propertyType,
            final BigDecimal minPrice, final BigDecimal maxPrice, final Integer minBedrooms) {
        this(suburb, listingType, propertyType, minPrice, maxPrice, minBedrooms, null);
    }
}
