package com.domain.listing.application;

import java.net.URL;
import java.time.Instant;
import java.util.UUID;

/** Capability returned to an authorized agent for one direct object-storage upload. */
public record MediaUploadAuthorization(UUID mediaId, URL uploadUrl, Instant expiresAt) {
}
