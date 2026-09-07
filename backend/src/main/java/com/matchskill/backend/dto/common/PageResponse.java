package com.matchskill.backend.dto.common;

import java.util.List;
import org.springframework.data.domain.Page;

/** The items plus the total count, for every paginated list endpoint. */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
