package com.momknpay.catalog.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.momknpay.catalog.domain.BillerService;
import com.momknpay.catalog.domain.ServiceCategory;
import java.time.Instant;

/** One catalogue entry as the clients store it locally. Amounts are piastres. */
public record ServiceItem(
        String id,
        String nameEn,
        String nameAr,
        ServiceCategory category,
        String iconUrl,
        String inputLabel,
        String inputPattern,
        long minAmount,
        long maxAmount,
        @JsonProperty("isActive") boolean isActive,
        Instant updatedAt) {

    public static ServiceItem from(BillerService service) {
        return new ServiceItem(
                service.getId(),
                service.getNameEn(),
                service.getNameAr(),
                service.getCategory(),
                service.getIconUrl(),
                service.getInputLabel(),
                service.getInputPattern(),
                service.getMinAmount(),
                service.getMaxAmount(),
                service.isActive(),
                service.getUpdatedAt());
    }
}
