package com.domain.listing.domain.model;

/**
 * Lifecycle states owned by the Listing bounded context.
 */
public enum PropertyStatus {
    DRAFT,
    ACTIVE,
    UNDER_OFFER,
    SOLD,
    RENTED,
    WITHDRAWN;

    /**
     * Checks whether this lifecycle state may transition to the supplied state.
     *
     * @param target requested state
     * @return true only for a permitted transition
     */
    public boolean canTransitionTo(final PropertyStatus target) {
        return switch (this) {
            case DRAFT -> target == ACTIVE || target == WITHDRAWN;
            case ACTIVE -> target == UNDER_OFFER || target == SOLD
                    || target == RENTED || target == WITHDRAWN;
            case UNDER_OFFER -> target == ACTIVE || target == SOLD || target == WITHDRAWN;
            case SOLD, RENTED, WITHDRAWN -> false;
        };
    }
}
