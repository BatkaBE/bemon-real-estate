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
        PropertyAddress address,
        PriceCurrency currency) {
    /** Defaults newly created listings to the Mongolian market. */
    public PropertyDraft(final String title, final PropertyType propertyType, final ListingType listingType,
            final BigDecimal price, final Integer bedrooms, final Integer bathrooms, final Integer parkingSpaces,
            final BigDecimal landSizeSqm, final PropertyAddress address) {
        this(title, propertyType, listingType, price, bedrooms, bathrooms, parkingSpaces, landSizeSqm,
                address, PriceCurrency.MNT);
    }

    /** Resolves an omitted replacement denomination after the owner and version have been verified. */
    public PropertyDraft withDefaultCurrency(final PriceCurrency defaultCurrency) {
        return new PropertyDraft(title, propertyType, listingType, price, bedrooms, bathrooms,
                parkingSpaces, landSizeSqm, address, currency == null ? defaultCurrency : currency);
    }
}
