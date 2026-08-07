package com.domain.listing.domain.port;

import java.net.URL;

/** Object-storage port used to grant a time-limited direct upload capability. */
public interface MediaStorage {
    /** Generates a PUT URL that is bound to the key, type, and content length. */
    URL presignPut(String objectKey, String contentType, long contentLength);
}
