package com.domain.listing.web.dto;

import com.domain.listing.domain.model.ListingType;
import com.domain.listing.domain.model.PropertyAddress;
import com.domain.listing.domain.model.PropertyDraft;
import com.domain.listing.domain.model.PropertyType;
import com.domain.listing.domain.model.PriceCurrency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Shared validated request body for property creation and replacement. */
public record PropertyMutationRequest(
        @NotBlank @Size(max = 255) String title,
        @NotNull PropertyType propertyType,
        @NotNull ListingType listingType,
        @PositiveOrZero @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Min(0) @Max(50) Integer bedrooms,
        @Min(0) @Max(50) Integer bathrooms,
        @Min(0) @Max(50) Integer parkingSpaces,
        @PositiveOrZero @Digits(integer = 8, fraction = 2) BigDecimal landSizeSqm,
        @NotNull @Valid PropertyAddressRequest address,
        PriceCurrency currency) {

    /** Maps this transport DTO into the Listing domain's editable value object. */
    public PropertyDraft toDraft() {
        return toDraft(PriceCurrency.MNT);
    }

    /** Keeps an existing denomination on replacement when an older client omits currency. */
    public PropertyDraft toDraft(final PriceCurrency defaultCurrency) {
        return new PropertyDraft(
                title,
                propertyType,
                listingType,
                price,
                bedrooms,
                bathrooms,
                parkingSpaces,
                landSizeSqm,
                new PropertyAddress(
                        address.addressLine(),
                        address.suburb(),
                        address.state(),
                        address.postcode(),
                        address.latitude(),
                        address.longitude()),
                currency == null ? defaultCurrency : currency);
    }
}
