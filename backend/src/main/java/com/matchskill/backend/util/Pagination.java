package com.matchskill.backend.util;

import com.matchskill.backend.exception.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

/** Shared bounds for paginated HTTP endpoints. */
public final class Pagination {

    private static final int MAX_PAGE_SIZE = 100;

    private Pagination() {}

    public static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "page must be non-negative and size must be positive");
        }
        int effectiveSize = Math.min(size, MAX_PAGE_SIZE);
        if ((long) page * effectiveSize > Integer.MAX_VALUE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "The requested page exceeds the supported offset");
        }
        return PageRequest.of(page, effectiveSize);
    }
}
