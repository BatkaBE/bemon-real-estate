package com.domain.listing.domain.model;

import java.util.UUID;

/** Signals that a requested listing is absent from the Listing bounded context. */
public class PropertyNotFoundException extends RuntimeException {
    /** Creates a not-found exception for one listing identifier. */
    public PropertyNotFoundException(final UUID propertyId) {
        super("Property not found: " + propertyId);
    }
}
