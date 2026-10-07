package com.domain.listing.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.domain.listing.domain.model.ListingType;
import com.domain.listing.domain.model.PropertyAddress;
import com.domain.listing.domain.model.PropertyDraft;
import com.domain.listing.domain.model.PropertyType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Checks that decimal formatting does not change idempotency identity. */
class RequestFingerprintTest {
    /** Database decimal normalization and JSON scale changes describe the same value. */
    @Test
    void normalizesEquivalentDecimals() {
        assertThat(RequestFingerprint.forDraft(draft("100")))
                .isEqualTo(RequestFingerprint.forDraft(draft("100.00")));
    }

    /** Builds a minimal valid draft with variable price formatting. */
    private PropertyDraft draft(final String price) {
        return new PropertyDraft("House", PropertyType.HOUSE, ListingType.SALE,
                new BigDecimal(price), 2, 1, 0, null,
                new PropertyAddress("1 Example Street", "Richmond", "VIC", "3121", -37.8, 145.0));
    }
}
