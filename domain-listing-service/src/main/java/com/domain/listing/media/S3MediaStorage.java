package com.domain.listing.media;

import com.domain.listing.domain.port.MediaStorage;
import com.domain.listing.media.config.MediaProperties;
import java.net.URL;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** AWS S3 adapter that signs a constrained HTTP PUT request for one private object. */
@Component
@ConditionalOnProperty(name = "app.media.enabled", havingValue = "true")
public class S3MediaStorage implements MediaStorage {
    private final S3Presigner presigner;
    private final MediaProperties properties;

    /** Creates the storage adapter with its signer and immutable configuration. */
    public S3MediaStorage(final S3Presigner presigner, final MediaProperties properties) {
        this.presigner = presigner;
        this.properties = properties;
    }

    @Override
    public URL presignPut(final String objectKey, final String contentType, final long contentLength) {
        final PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(properties.presignDuration())
                .putObjectRequest(putRequest)
                .build()).url();
    }
}
