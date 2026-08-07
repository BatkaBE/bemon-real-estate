package com.domain.listing.domain.port;

import com.domain.listing.domain.model.PropertyMedia;

/** Persistence port for property-media metadata. */
public interface PropertyMediaRepository {
    /** Persists a pending or verified media record. */
    PropertyMedia save(PropertyMedia media);
}
