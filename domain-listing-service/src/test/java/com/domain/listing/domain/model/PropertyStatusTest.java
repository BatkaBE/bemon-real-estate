package com.domain.listing.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies the listing lifecycle rules independently from framework and database concerns.
 */
class PropertyStatusTest {

    /**
     * A draft listing can become active when the agent publishes it.
     */
    @Test
    void draftCanBecomeActive() {
        assertThat(PropertyStatus.DRAFT.canTransitionTo(PropertyStatus.ACTIVE)).isTrue();
    }

    /**
     * A terminal sold listing cannot return to active status.
     */
    @Test
    void soldCannotBecomeActive() {
        assertThat(PropertyStatus.SOLD.canTransitionTo(PropertyStatus.ACTIVE)).isFalse();
    }
}
