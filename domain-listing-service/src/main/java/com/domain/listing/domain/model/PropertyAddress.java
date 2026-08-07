package com.domain.listing.domain.model;

/** Geographic address associated with a property listing. */
public record PropertyAddress(
        String addressLine,
        String suburb,
        String state,
        String postcode,
        double latitude,
        double longitude) {
}
