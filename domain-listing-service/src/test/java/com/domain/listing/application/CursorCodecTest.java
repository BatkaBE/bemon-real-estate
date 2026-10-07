package com.domain.listing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.domain.listing.domain.model.PropertyCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Verifies that malformed cursors consistently become client errors. */
class CursorCodecTest {
    /** Valid keys preserve timestamp precision and identifier. */
    @Test
    void roundTripsCursor() {
        final PropertyCursor cursor = new PropertyCursor(Instant.parse("2026-10-05T01:02:03.123456Z"),
                UUID.randomUUID());
        assertThat(CursorCodec.decode(CursorCodec.encode(cursor))).isEqualTo(cursor);
    }

    /** An invalid timestamp must not escape as an unhandled DateTimeParseException. */
    @Test
    void rejectsInvalidTimestamp() {
        final String value = Base64.getUrlEncoder().encodeToString(
                ("not-a-date|" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> CursorCodec.decode(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
