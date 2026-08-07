package com.domain.identity.web.dto;

import com.domain.identity.domain.AccountRole;
import com.domain.identity.domain.PlatformUser;
import java.time.Instant;
import java.util.UUID;

/** Safe public representation of a created account. */
public record UserResponse(UUID id, String email, AccountRole role, Instant createdAt) {
    /** Maps a persisted user without exposing its password hash. */
    public static UserResponse from(final PlatformUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
    }
}
