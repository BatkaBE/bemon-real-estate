package com.domain.listing.domain.model;

/** Signals a mutation attempt by an agent who does not own the listing. */
public class PropertyOwnershipException extends RuntimeException {
    /** Creates a generic ownership exception without leaking listing data. */
    public PropertyOwnershipException() {
        super("The authenticated agent does not own this property listing");
    }
}
