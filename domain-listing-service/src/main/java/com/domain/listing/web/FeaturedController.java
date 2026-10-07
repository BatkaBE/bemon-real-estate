package com.domain.listing.web;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
/** Stores immutable paid grants and exposes only active, unexpired public featured state. */
@RestController
public class FeaturedController {
    private final JdbcTemplate db;private final String key;private final com.domain.listing.application.PropertyApplicationService properties;
    /** Receives the authenticated payment boundary's service credential. */
    public FeaturedController(final JdbcTemplate db,final com.domain.listing.application.PropertyApplicationService properties,@Value("${app.internal-key:}") final String key){this.db=db;this.key=key;this.properties=properties;}
    /** Resolves ownership and eligibility without accepting a client-supplied agent identity. */
    @GetMapping("/internal/properties/{id}/eligibility") public Map<String,Object> eligible(@PathVariable final UUID id,@RequestHeader(value="X-Internal-Key",defaultValue="") final String supplied){authorize(supplied);final var rows=db.queryForList("SELECT agent_id AS \"agentId\",status,currency FROM properties WHERE id=?",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);return rows.get(0);}
    /** Idempotently delivers a fixed grant and rejects changed ownership or expiry on replay. */
    @PutMapping("/internal/featured/{id}") @Transactional public Map<String,String> grant(@PathVariable final UUID id,@RequestHeader(value="X-Internal-Key",defaultValue="") final String supplied,@Valid @RequestBody final Grant body){
        authorize(supplied);db.queryForObject("SELECT pg_advisory_xact_lock(?)",Object.class,id.getMostSignificantBits()^id.getLeastSignificantBits());
        final var owner=db.queryForList("SELECT agent_id FROM properties WHERE id=?",body.propertyId());
        if(owner.isEmpty()||!body.agentId().equals(owner.get(0).get("agent_id")))throw new ResponseStatusException(HttpStatus.CONFLICT);
        final var existing=db.queryForList("SELECT * FROM featured_grants WHERE id=? FOR UPDATE",id);
        if(existing.isEmpty())db.update("INSERT INTO featured_grants(id,property_id,agent_id,expires_at,revoked_at) VALUES(?,?,?,?,?)",id,body.propertyId(),body.agentId(),java.sql.Timestamp.from(body.expiresAt()),body.revoked()?java.sql.Timestamp.from(Instant.now()):null);
        else{final var row=existing.get(0);if(!row.get("property_id").equals(body.propertyId())||!row.get("agent_id").equals(body.agentId())||!((java.sql.Timestamp)row.get("expires_at")).toInstant().equals(body.expiresAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)))throw new ResponseStatusException(HttpStatus.CONFLICT);
            if(body.revoked())db.update("UPDATE featured_grants SET revoked_at=COALESCE(revoked_at,now()) WHERE id=?",id);}
        return Map.of("status","accepted");
    }
    /** Lists the newest unexpired public paid placements with a bounded result size. */
    @GetMapping("/v1/properties/featured") public Map<String,Object> placements(){
        final var ids=db.queryForList("SELECT g.property_id FROM featured_grants g JOIN properties p ON p.id=g.property_id WHERE g.expires_at>now() AND g.revoked_at IS NULL AND p.status='ACTIVE' AND p.currency='MNT' GROUP BY g.property_id ORDER BY max(g.expires_at) DESC LIMIT 6",UUID.class);
        return Map.of("items",ids.stream().map(id->com.domain.listing.web.dto.PropertyResponse.from(properties.get(id,null))).toList());
    }
    /** Uses current publication state so withdrawn listings cannot remain featured. */
    @GetMapping("/v1/properties/{id}/featured") public Map<String,Boolean> featured(@PathVariable final UUID id){return Map.of("featured",Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM featured_grants g JOIN properties p ON p.id=g.property_id WHERE g.property_id=? AND g.expires_at>now() AND g.revoked_at IS NULL AND p.status='ACTIVE')",Boolean.class,id)));}
    /** Rejects absent service credentials. */
    private void authorize(final String supplied){if(key.isBlank()||!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))throw new AccessDeniedException("Invalid service credential");}
    public record Grant(@NotNull UUID propertyId,@NotNull UUID agentId,@NotNull Instant expiresAt,boolean revoked){}
}
