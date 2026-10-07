package com.domain.listing.web;

import com.domain.listing.application.EngagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** Cookie-free authenticated engagement APIs use the actual JWT subject for every ownership decision. */
@RestController
public class EngagementController {
    private final EngagementService service;
    /** Injects participant-scoped engagement operations. */
    public EngagementController(final EngagementService service){this.service=service;}
    /** Returns saved listings for the caller. */
    @GetMapping("/v1/users/me/favorites")
    public Map<String,Object> favorites(@AuthenticationPrincipal final Jwt jwt,@RequestParam(required=false) final String cursor){return service.favorites(id(jwt),cursor);}
    /** Reports the caller's saved state. */
    @GetMapping("/v1/users/me/favorites/{propertyId}")
    public Map<String,Boolean> favorite(@AuthenticationPrincipal final Jwt jwt,@PathVariable final UUID propertyId){return Map.of("saved",service.isFavorite(id(jwt),propertyId));}
    /** Adds a favorite idempotently. */
    @PutMapping("/v1/users/me/favorites/{propertyId}")
    public Map<String,Boolean> save(@AuthenticationPrincipal final Jwt jwt,@PathVariable final UUID propertyId){service.favorite(id(jwt),propertyId);return Map.of("saved",true);}
    /** Removes only this user's relationship. */
    @DeleteMapping("/v1/users/me/favorites/{propertyId}")
    public Map<String,Boolean> remove(@AuthenticationPrincipal final Jwt jwt,@PathVariable final UUID propertyId){service.unfavorite(id(jwt),propertyId);return Map.of("saved",false);}
    /** Creates one contact request without accepting a buyer ID, agent ID, or sender email. */
    @PostMapping("/v1/properties/{propertyId}/inquiries")
    public Map<String,Object> create(@AuthenticationPrincipal final Jwt jwt,@PathVariable final UUID propertyId,
            @RequestHeader("Idempotency-Key") final UUID key,@Valid @RequestBody final Message body){return service.inquire(id(jwt),propertyId,key,body.message(),jwt.getTokenValue());}
    /** Lists only conversations in which the authenticated account participates. */
    @GetMapping("/v1/users/me/inquiries")
    public Map<String,Object> inquiries(@AuthenticationPrincipal final Jwt jwt,@RequestParam(required=false) final String cursor){return service.inquiries(id(jwt),jwt.getClaimAsStringList("roles")!=null&&jwt.getClaimAsStringList("roles").contains("ROLE_AGENT"),cursor);}
    /** Authorizes agent replies before enforcing participant ownership and a strong ETag. */
    @PatchMapping("/v1/inquiries/{inquiryId}") @PreAuthorize("hasRole('AGENT')")
    public Map<String,Object> reply(@AuthenticationPrincipal final Jwt jwt,@PathVariable final UUID inquiryId,
            @RequestHeader("If-Match") final String tag,@Valid @RequestBody final Reply body){
        if(!tag.matches("\"[0-9]{1,18}\""))throw new IllegalArgumentException("Invalid ETag");
        return service.reply(id(jwt),inquiryId,Long.parseLong(tag.substring(1,tag.length()-1)),body.status(),body.reply());
    }
    /** Resolves immutable principal IDs. */
    private UUID id(final Jwt jwt){return UUID.fromString(jwt.getSubject());}
    public record Message(@NotBlank @Size(max=2000) String message){@Override public String toString(){return "Message[redacted]";}}
    public record Reply(@NotNull @Size(max=20) String status,@NotNull @Size(max=2000) String reply){}
}
