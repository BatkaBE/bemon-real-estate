package com.domain.listing.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Validated address payload accepted by listing mutations. */
public record PropertyAddressRequest(
        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 100) String suburb,
        @NotBlank @Size(min = 2, max = 10) String state,
        @NotBlank @Size(min = 4, max = 10) String postcode,
        @NotNull @Min(-90) @Max(90) Double latitude,
        @NotNull @Min(-180) @Max(180) Double longitude) {
}
