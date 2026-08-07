package com.domain.listing.persistence;

import com.domain.listing.domain.model.PropertyMedia;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data implementation detail for property-media metadata. */
interface JpaPropertyMediaRepository extends JpaRepository<PropertyMedia, UUID> {
}
