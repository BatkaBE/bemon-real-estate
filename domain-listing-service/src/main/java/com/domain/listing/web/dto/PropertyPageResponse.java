package com.domain.listing.web.dto;

import com.domain.listing.application.PropertySearchPage;
import java.util.List;

/** Public cursor-paginated response envelope for property discovery. */
public record PropertyPageResponse(List<PropertyResponse> items, String nextCursor, int pageSize) {
    /** Maps an application search result into a transport-only response envelope. */
    public static PropertyPageResponse from(final PropertySearchPage page) {
        return new PropertyPageResponse(
                page.items().stream().map(PropertyResponse::from).toList(),
                page.nextCursor(),
                page.pageSize());
    }
}
