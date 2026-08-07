package com.domain.listing.application;

import com.domain.listing.domain.model.PropertyAddress;
import com.domain.listing.domain.model.PropertyDraft;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Produces a deterministic SHA-256 fingerprint for replay-safe create requests. */
final class RequestFingerprint {
    private RequestFingerprint() {
    }

    /** Returns the fingerprint of the semantically relevant create request fields. */
    static String forDraft(final PropertyDraft draft) {
        final PropertyAddress address = draft.address();
        final String canonical = String.join("|",
                value(draft.title()), value(draft.propertyType()), value(draft.listingType()),
                value(draft.price()), value(draft.bedrooms()), value(draft.bathrooms()),
                value(draft.parkingSpaces()), value(draft.landSizeSqm()), value(address.addressLine()),
                value(address.suburb()), value(address.state()), value(address.postcode()),
                value(address.latitude()), value(address.longitude()));
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in the JVM", exception);
        }
    }

    private static String value(final Object input) {
        return input == null ? "<null>" : input.toString().replace("|", "\\|");
    }
}
