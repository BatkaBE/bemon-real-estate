package com.domain.listing.application;

import com.domain.listing.domain.model.Property;
import java.util.List;

/** Application result for a keyset-paginated public listing browse request. */
public record PropertySearchPage(List<Property> items, String nextCursor, int pageSize) {
}
