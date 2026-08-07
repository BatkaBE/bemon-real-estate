package com.domain.listing.persistence;

import com.domain.listing.domain.model.Property;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Spring Data implementation detail for the properties table. */
interface JpaPropertyRepository extends JpaRepository<Property, UUID>, JpaSpecificationExecutor<Property> {
}
