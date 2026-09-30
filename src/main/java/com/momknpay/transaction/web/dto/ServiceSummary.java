package com.momknpay.transaction.web.dto;

import com.momknpay.catalog.domain.BillerService;
import com.momknpay.catalog.domain.ServiceCategory;

/** The biller as shown on a history row or receipt, in both languages. */
public record ServiceSummary(String id, String nameEn, String nameAr, ServiceCategory category) {

    public static ServiceSummary from(BillerService service) {
        return new ServiceSummary(
                service.getId(), service.getNameEn(), service.getNameAr(), service.getCategory());
    }
}
