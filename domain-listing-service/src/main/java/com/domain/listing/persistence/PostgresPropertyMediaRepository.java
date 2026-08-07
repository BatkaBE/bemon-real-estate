package com.domain.listing.persistence;

import com.domain.listing.domain.model.PropertyMedia;
import com.domain.listing.domain.port.PropertyMediaRepository;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter for media metadata persistence. */
@Repository
public class PostgresPropertyMediaRepository implements PropertyMediaRepository {
    private final JpaPropertyMediaRepository delegate;

    /** Creates the adapter with its Spring Data delegate. */
    public PostgresPropertyMediaRepository(final JpaPropertyMediaRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public PropertyMedia save(final PropertyMedia media) {
        return delegate.save(media);
    }
}
