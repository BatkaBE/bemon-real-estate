package com.domain.listing.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Metadata required to create one constrained direct image-upload URL. */
public record PresignImageUploadRequest(
        @NotBlank String contentType,
        @NotNull @Min(1) @Max(26214400) Long contentLength,
        @NotNull @Min(0) @Max(32767) Short displayOrder) {
}
