package com.momknpay.transaction.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Page envelope of the contract: 0-based page, total counts. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
