package com.domain.listing.domain.model;

/**
 * Signals an attempted lifecycle transition that violates the listing rules.
 */
public class InvalidPropertyStatusTransitionException extends RuntimeException {

    /**
     * Creates an exception describing the rejected state transition.
     *
     * @param current current listing status
     * @param target requested listing status
     */
    public InvalidPropertyStatusTransitionException(
            final PropertyStatus current,
            final PropertyStatus target) {
        super("Property status cannot transition from " + current + " to " + target);
    }
}
