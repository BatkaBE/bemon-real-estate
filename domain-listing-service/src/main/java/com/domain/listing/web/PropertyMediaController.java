package com.domain.listing.web;

import com.domain.listing.application.PropertyApplicationService;
import com.domain.listing.web.dto.PresignImageUploadRequest;
import com.domain.listing.web.dto.PresignImageUploadResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for property-media upload capabilities. */
@RestController
@RequestMapping("/v1/properties/{propertyId}/media")
public class PropertyMediaController {
    private final PropertyApplicationService propertyApplicationService;

    /** Creates the controller with its application service dependency. */
    public PropertyMediaController(final PropertyApplicationService propertyApplicationService) {
        this.propertyApplicationService = propertyApplicationService;
    }

    /** Returns a short-lived S3 PUT URL after verifying property ownership. */
    @PostMapping("/upload-url")
    @PreAuthorize("hasRole('AGENT') and hasAuthority('SCOPE_listings:write')")
    public ResponseEntity<PresignImageUploadResponse> requestUploadUrl(
            @AuthenticationPrincipal final Jwt jwt,
            @PathVariable final UUID propertyId,
            @Valid @RequestBody final PresignImageUploadRequest request) {
        return ResponseEntity.ok(PresignImageUploadResponse.from(
                propertyApplicationService.requestImageUpload(
                        UUID.fromString(jwt.getSubject()),
                        propertyId,
                        request.contentType(),
                        request.contentLength(),
                        request.displayOrder())));
    }
}
