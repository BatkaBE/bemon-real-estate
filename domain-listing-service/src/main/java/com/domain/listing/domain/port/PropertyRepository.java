package com.domain.listing.domain.port;

import com.domain.listing.domain.model.Property;
import com.domain.listing.domain.model.PropertyCursor;
import com.domain.listing.domain.model.PropertySearchCriteria;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port owned by the Listing domain. */
public interface PropertyRepository {
    /** Persists a new or modified property aggregate. */
    Property save(Property property);

    /** Locates a property by its public identifier. */
    Optional<Property> findById(UUID propertyId);

    /** Finds a keyset-paginated, public page of active properties. */
    List<Property> findActive(
            PropertySearchCriteria criteria,
            PropertyCursor cursor,
            int maximumResults);
}
