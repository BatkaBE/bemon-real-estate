package com.domain.listing.application;

import com.domain.listing.domain.model.PropertyAddress;
import com.domain.listing.domain.model.PropertyDraft;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;

/** Produces a deterministic SHA-256 fingerprint for replay-safe create requests. */
final class RequestFingerprint {
    private RequestFingerprint() {
    }

    /** Returns the fingerprint of the semantically relevant create request fields. */
    static String forDraft(final PropertyDraft draft) {
        return hash(forPreviousDraft(draft) + "|" + value(draft.currency()));
    }

    /** Recognizes the response-retaining format used before currency became explicit. */
    static String forPreviousDraft(final PropertyDraft draft) {
        final PropertyAddress address = draft.address();
        final String canonical = String.join("|",
                value(draft.title()), value(draft.propertyType()), value(draft.listingType()),
                value(draft.price()), value(draft.bedrooms()), value(draft.bathrooms()),
                value(draft.parkingSpaces()), value(draft.landSizeSqm()), value(address.addressLine()),
                value(address.suburb()), value(address.state()), value(address.postcode()),
                value(address.latitude()), value(address.longitude()));
        return hash(canonical);
    }

    /** Recognizes unexpired records written before length-prefixed fingerprints were introduced. */
    static String forLegacyDraft(final PropertyDraft draft) {
        final PropertyAddress address = draft.address();
        final String canonical = String.join("|",
                legacyValue(draft.title()), legacyValue(draft.propertyType()), legacyValue(draft.listingType()),
                legacyValue(draft.price()), legacyValue(draft.bedrooms()), legacyValue(draft.bathrooms()),
                legacyValue(draft.parkingSpaces()), legacyValue(draft.landSizeSqm()), legacyValue(address.addressLine()),
                legacyValue(address.suburb()), legacyValue(address.state()), legacyValue(address.postcode()),
                legacyValue(address.latitude()), legacyValue(address.longitude()));
        return hash(canonical);
    }

    /** Produces the SHA-256 digest shared by both retained fingerprint formats. */
    private static String hash(final String canonical) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in the JVM", exception);
        }
    }

    /** Preserves the previous escaping and null encoding for legacy replay records only. */
    private static String legacyValue(final Object input) {
        return input == null ? "<null>" : input.toString().replace("|", "\\|");
    }

    private static String value(final Object input) {
        if (input == null) {
            return "-1:";
        }
        final String text = input instanceof BigDecimal decimal
                ? decimal.stripTrailingZeros().toPlainString() : input.toString();
        return text.length() + ":" + text;
    }
}
