package com.domain.listing.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Transactional aggregate for a property listing. Search projections are maintained outside this model.
 */
@Entity
@Table(name = "properties")
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agent_id", nullable = false, updatable = false)
    private UUID agentId;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 50)
    private PropertyType propertyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false, length = 20)
    private ListingType listingType;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private PriceCurrency currency;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer bedrooms;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer bathrooms;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "parking_spaces")
    private Integer parkingSpaces;

    @Column(name = "land_size_sqm", precision = 10, scale = 2)
    private BigDecimal landSizeSqm;

    @Column(name = "address_line", nullable = false, length = 255)
    private String addressLine;

    @Column(nullable = false, length = 100)
    private String suburb;

    @Column(nullable = false, length = 10)
    private String state;

    @Column(nullable = false, length = 10)
    private String postcode;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PropertyStatus status;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Required by JPA.
     */
    protected Property() {
    }

    /** Creates a new draft listing owned by one agent. */
    public static Property create(final UUID agentId, final PropertyDraft draft, final Clock clock) {
        final Property property = new Property();
        property.agentId = Objects.requireNonNull(agentId, "agentId must not be null");
        property.apply(draft);
        property.status = PropertyStatus.DRAFT;
        property.createdAt = Instant.now(clock);
        property.updatedAt = property.createdAt;
        return property;
    }

    /** Replaces the editable listing attributes. */
    public void replace(final PropertyDraft draft, final Clock clock) {
        apply(draft);
        updatedAt = Instant.now(clock);
    }

    /**
     * Applies a validated lifecycle change.
     *
     * @param target the requested status
     */
    public void transitionTo(final PropertyStatus target, final Clock clock) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidPropertyStatusTransitionException(status, target);
        }
        status = target;
        updatedAt = Instant.now(clock);
    }

    /** Checks whether the supplied agent owns this aggregate. */
    public boolean isOwnedBy(final UUID candidateAgentId) {
        return agentId.equals(candidateAgentId);
    }

    public UUID getId() { return id; }
    public UUID getAgentId() { return agentId; }
    public String getTitle() { return title; }
    public PropertyType getPropertyType() { return propertyType; }
    public ListingType getListingType() { return listingType; }
    public BigDecimal getPrice() { return price; }
    /** Returns the original denomination of this listing's price. */
    public PriceCurrency getCurrency() { return currency; }
    public Integer getBedrooms() { return bedrooms; }
    public Integer getBathrooms() { return bathrooms; }
    public Integer getParkingSpaces() { return parkingSpaces; }
    public BigDecimal getLandSizeSqm() { return landSizeSqm; }
    public String getAddressLine() { return addressLine; }
    public String getSuburb() { return suburb; }
    public String getState() { return state; }
    public String getPostcode() { return postcode; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public PropertyStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private void apply(final PropertyDraft draft) {
        title = draft.title();
        propertyType = draft.propertyType();
        listingType = draft.listingType();
        price = draft.price();
        currency = Objects.requireNonNull(draft.currency(), "currency must not be null");
        bedrooms = draft.bedrooms();
        bathrooms = draft.bathrooms();
        parkingSpaces = draft.parkingSpaces();
        landSizeSqm = draft.landSizeSqm();
        addressLine = draft.address().addressLine();
        suburb = draft.address().suburb();
        state = draft.address().state();
        postcode = draft.address().postcode();
        latitude = draft.address().latitude();
        longitude = draft.address().longitude();
    }
}
