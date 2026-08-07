package com.domain.listing.web;

import com.domain.listing.application.PropertyApplicationService;
import com.domain.listing.domain.model.Property;
import com.domain.listing.domain.model.ListingType;
import com.domain.listing.domain.model.PropertySearchCriteria;
import com.domain.listing.domain.model.PropertyType;
import com.domain.listing.web.dto.ChangePropertyStatusRequest;
import com.domain.listing.web.dto.PropertyMutationRequest;
import com.domain.listing.web.dto.PropertyResponse;
import com.domain.listing.web.dto.PropertyPageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the transactional Listing API. */
@RestController
@RequestMapping("/v1/properties")
public class PropertyController {
    private final PropertyApplicationService propertyApplicationService;

    /** Creates the controller with its application service dependency. */
    public PropertyController(final PropertyApplicationService propertyApplicationService) {
        this.propertyApplicationService = propertyApplicationService;
    }

    /** Creates a draft listing for the authenticated agent. */
    @PostMapping
    @PreAuthorize("hasRole('AGENT') and hasAuthority('SCOPE_listings:write')")
    public ResponseEntity<PropertyResponse> create(
            @AuthenticationPrincipal final Jwt jwt,
            @RequestHeader("Idempotency-Key") final UUID idempotencyKey,
            @Valid @RequestBody final PropertyMutationRequest request) {
        final Property property = propertyApplicationService.create(
                agentId(jwt), idempotencyKey, request.toDraft());
        return ResponseEntity.created(URI.create("/v1/properties/" + property.getId()))
                .header(HttpHeaders.ETAG, entityTag(property))
                .body(PropertyResponse.from(property));
    }

    /** Browses active listings with filters and a stable keyset cursor. */
    @GetMapping
    public PropertyPageResponse list(
            @RequestParam(required = false) final String suburb,
            @RequestParam(required = false) final ListingType listingType,
            @RequestParam(required = false) final PropertyType propertyType,
            @RequestParam(required = false) final BigDecimal minPrice,
            @RequestParam(required = false) final BigDecimal maxPrice,
            @RequestParam(required = false) final Integer minBedrooms,
            @RequestParam(required = false) final String cursor,
            @RequestParam(defaultValue = "20") final int pageSize) {
        return PropertyPageResponse.from(propertyApplicationService.search(
                new PropertySearchCriteria(
                        suburb, listingType, propertyType, minPrice, maxPrice, minBedrooms),
                cursor,
                pageSize));
    }

    /** Retrieves one property listing. */
    @GetMapping("/{propertyId}")
    public ResponseEntity<PropertyResponse> get(@PathVariable final UUID propertyId) {
        final Property property = propertyApplicationService.get(propertyId);
        return ResponseEntity.ok().header(HttpHeaders.ETAG, entityTag(property))
                .body(PropertyResponse.from(property));
    }

    /** Replaces an agent-owned listing if the supplied entity tag is current. */
    @PutMapping("/{propertyId}")
    @PreAuthorize("hasRole('AGENT') and hasAuthority('SCOPE_listings:write')")
    public ResponseEntity<PropertyResponse> update(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final UUID propertyId,
            @RequestHeader(HttpHeaders.IF_MATCH) final String ifMatch,
            @Valid @RequestBody final PropertyMutationRequest request) {
        final Property property = propertyApplicationService.update(
                agentId(jwt), propertyId, parseEntityTag(ifMatch), request.toDraft());
        return ResponseEntity.ok().header(HttpHeaders.ETAG, entityTag(property))
                .body(PropertyResponse.from(property));
    }

    /** Applies an allowed status transition to an agent-owned listing. */
    @PatchMapping("/{propertyId}/status")
    @PreAuthorize("hasRole('AGENT') and hasAuthority('SCOPE_listings:write')")
    public ResponseEntity<PropertyResponse> changeStatus(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final UUID propertyId,
            @RequestHeader(HttpHeaders.IF_MATCH) final String ifMatch,
            @Valid @RequestBody final ChangePropertyStatusRequest request) {
        final Property property = propertyApplicationService.changeStatus(
                agentId(jwt), propertyId, parseEntityTag(ifMatch), request.status());
        return ResponseEntity.ok().header(HttpHeaders.ETAG, entityTag(property))
                .body(PropertyResponse.from(property));
    }

    private UUID agentId(final Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String entityTag(final Property property) {
        return "\"" + property.getVersion() + "\"";
    }

    private long parseEntityTag(final String entityTag) {
        try {
            return Long.parseLong(entityTag.replace("\"", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match must contain a numeric entity tag");
        }
    }
}
