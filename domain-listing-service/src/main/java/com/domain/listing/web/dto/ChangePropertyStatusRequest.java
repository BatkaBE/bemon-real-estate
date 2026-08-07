package com.domain.listing.web.dto;

import com.domain.listing.domain.model.PropertyStatus;
import jakarta.validation.constraints.NotNull;

/** Validated lifecycle-transition request. */
public record ChangePropertyStatusRequest(@NotNull PropertyStatus status) {
}
