package com.domain.listing.domain.model;

/** Signals a conditional mutation made with an obsolete entity tag. */
public class StalePropertyVersionException extends RuntimeException {
    /** Creates a stale-version exception without exposing concurrent changes. */
    public StalePropertyVersionException() {
        super("The property listing has been modified by another request");
    }
}
