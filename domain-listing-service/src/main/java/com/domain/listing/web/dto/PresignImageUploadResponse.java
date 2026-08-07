package com.domain.listing.web.dto;

import com.domain.listing.application.MediaUploadAuthorization;
import java.time.Instant;
import java.util.UUID;

/** Short-lived direct-upload capability returned after property ownership validation. */
public record PresignImageUploadResponse(UUID mediaId, String uploadUrl, Instant expiresAt) {
    /** Converts the application result to a JSON-safe transport response. */
    public static PresignImageUploadResponse from(final MediaUploadAuthorization authorization) {
        return new PresignImageUploadResponse(
                authorization.mediaId(), authorization.uploadUrl().toExternalForm(), authorization.expiresAt());
    }
}
