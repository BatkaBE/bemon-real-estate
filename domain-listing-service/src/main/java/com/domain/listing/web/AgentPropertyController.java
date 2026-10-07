package com.domain.listing.web;

import com.domain.listing.application.PropertyApplicationService;
import com.domain.listing.web.dto.PropertyPageResponse;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Private dashboard read adapter with ownership derived solely from the verified JWT. */
@RestController
@RequestMapping("/v1/agents/me/properties")
public class AgentPropertyController {
    private final PropertyApplicationService service;

    /** Receives the listing application use cases. */
    public AgentPropertyController(final PropertyApplicationService service) {
        this.service = service;
    }

    /** Returns the caller's listings without accepting an agent identifier from the client. */
    @GetMapping
    @PreAuthorize("hasRole('AGENT') and hasAuthority('SCOPE_listings:write')")
    public PropertyPageResponse list(@AuthenticationPrincipal final Jwt jwt,
            @RequestParam(required = false) final String cursor,
            @RequestParam(defaultValue = "20") final int pageSize) {
        return PropertyPageResponse.from(service.searchOwned(UUID.fromString(jwt.getSubject()), cursor, pageSize));
    }
}
