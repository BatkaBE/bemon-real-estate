package com.domain.listing.media;

import com.domain.listing.domain.model.MediaStorageUnavailableException;
import com.domain.listing.domain.port.MediaStorage;
import java.net.URL;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Allows the listing backend to run locally without AWS credentials or misleading upload URLs. */
@Component
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledMediaStorage implements MediaStorage {
    /** Rejects uploads explicitly; the surrounding transaction rolls back pending metadata. */
    @Override
    public URL presignPut(final String objectKey, final String contentType, final long contentLength) {
        throw new MediaStorageUnavailableException();
    }
}
