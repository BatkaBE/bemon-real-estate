package com.domain.listing.application;

import com.domain.listing.domain.model.PropertyCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.Base64;
import java.util.UUID;

/** Encodes and decodes opaque public keyset cursor values. */
final class CursorCodec {
    private CursorCodec() {
    }

    /** Encodes a database cursor for the API response. */
    static String encode(final PropertyCursor cursor) {
        final String raw = cursor.createdAt() + "|" + cursor.propertyId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes an API cursor or rejects malformed client input. */
    static PropertyCursor decode(final String value) {
        try {
            final String raw = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            final String[] parts = raw.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Cursor has an invalid format");
            }
            return new PropertyCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new IllegalArgumentException("Cursor has an invalid format", exception);
        }
    }
}
