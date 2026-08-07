package com.domain.listing.persistence;

import com.domain.listing.domain.model.Property;
import com.domain.listing.domain.model.PropertyCursor;
import com.domain.listing.domain.model.PropertySearchCriteria;
import com.domain.listing.domain.model.PropertyStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/** Builds query predicates for the PostgreSQL public-listing read path. */
final class PropertySpecifications {
    private PropertySpecifications() {
    }

    /** Returns a specification with filters and descending keyset-pagination predicate. */
    static Specification<Property> activeProperties(
            final PropertySearchCriteria criteria,
            final PropertyCursor cursor) {
        return (root, query, builder) -> {
            final List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("status"), PropertyStatus.ACTIVE));
            addFilters(criteria, root, builder, predicates);
            addCursor(cursor, root, builder, predicates);
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static void addFilters(
            final PropertySearchCriteria criteria,
            final jakarta.persistence.criteria.Root<Property> root,
            final jakarta.persistence.criteria.CriteriaBuilder builder,
            final List<Predicate> predicates) {
        if (criteria.suburb() != null) {
            predicates.add(builder.equal(root.get("suburb"), criteria.suburb()));
        }
        if (criteria.listingType() != null) {
            predicates.add(builder.equal(root.get("listingType"), criteria.listingType()));
        }
        if (criteria.propertyType() != null) {
            predicates.add(builder.equal(root.get("propertyType"), criteria.propertyType()));
        }
        if (criteria.minPrice() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("price"), criteria.minPrice()));
        }
        if (criteria.maxPrice() != null) {
            predicates.add(builder.lessThanOrEqualTo(root.get("price"), criteria.maxPrice()));
        }
        if (criteria.minBedrooms() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("bedrooms"), criteria.minBedrooms()));
        }
    }

    private static void addCursor(
            final PropertyCursor cursor,
            final jakarta.persistence.criteria.Root<Property> root,
            final jakarta.persistence.criteria.CriteriaBuilder builder,
            final List<Predicate> predicates) {
        if (cursor == null) {
            return;
        }
        final var createdAt = root.<Instant>get("createdAt");
        final var propertyId = root.<UUID>get("id");
        predicates.add(builder.or(
                builder.lessThan(createdAt, cursor.createdAt()),
                builder.and(
                        builder.equal(createdAt, cursor.createdAt()),
                        builder.lessThan(propertyId, cursor.propertyId()))));
    }
}
