package com.domain.listing.persistence;

import com.domain.listing.domain.model.Property;
import com.domain.listing.domain.model.PropertyCursor;
import com.domain.listing.domain.model.PropertySearchCriteria;
import com.domain.listing.domain.port.PropertyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter for the Listing domain persistence port. */
@Repository
public class PostgresPropertyRepository implements PropertyRepository {
    private final JpaPropertyRepository delegate;

    /** Creates the adapter with its Spring Data delegate. */
    public PostgresPropertyRepository(final JpaPropertyRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Property save(final Property property) {
        return delegate.saveAndFlush(property);
    }

    @Override
    public Optional<Property> findById(final UUID propertyId) {
        return delegate.findById(propertyId);
    }

    /** Retrieves private dashboard data using an explicit owner predicate. */
    @Override
    public List<Property> findOwned(final UUID agentId, final PropertyCursor cursor, final int maximumResults) {
        return delegate.findAll(PropertySpecifications.ownedProperties(agentId, cursor),
                PageRequest.of(0, maximumResults, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .getContent();
    }

    @Override
    public List<Property> findActive(
            final PropertySearchCriteria criteria,
            final PropertyCursor cursor,
            final int maximumResults) {
        return delegate.findAll(
                        PropertySpecifications.activeProperties(criteria, cursor),
                        PageRequest.of(0, maximumResults,
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .getContent();
    }
}
