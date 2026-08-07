package com.domain.listing.web.dto;

/** Address representation returned by the listing API. */
public record PropertyAddressResponse(
        String addressLine,
        String suburb,
        String state,
        String postcode,
        double latitude,
        double longitude) {
}
