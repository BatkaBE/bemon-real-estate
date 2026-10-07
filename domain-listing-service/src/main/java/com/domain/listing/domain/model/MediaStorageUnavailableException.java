package com.domain.listing.domain.model;

/** Indicates that media storage has not been enabled for this deployment. */
public class MediaStorageUnavailableException extends RuntimeException {
    /** Explains that clients should defer uploads until storage is configured. */
    public MediaStorageUnavailableException() {
        super("Media uploads are unavailable until object storage is configured");
    }
}
