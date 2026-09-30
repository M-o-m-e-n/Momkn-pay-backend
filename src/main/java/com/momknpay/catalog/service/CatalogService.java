package com.momknpay.catalog.service;

import com.momknpay.catalog.repository.BillerServiceRepository;
import com.momknpay.catalog.web.dto.CatalogResponse;
import com.momknpay.catalog.web.dto.ServiceItem;
import com.momknpay.common.util.TimeProvider;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The payable-services catalogue that clients cache for offline use (FR-CAT). */
@Service
public class CatalogService {

    private final BillerServiceRepository services;
    private final TimeProvider time;

    public CatalogService(BillerServiceRepository services, TimeProvider time) {
        this.services = services;
        this.time = time;
    }

    /** FR-CAT-1: every non-deleted service, inactive ones included (shown disabled). */
    @Transactional(readOnly = true)
    public CatalogResponse getAll() {
        Instant syncedAt = time.now(); // captured before reading, see sync()
        return new CatalogResponse(
                syncedAt,
                services.findByDeletedAtIsNullOrderByCategoryAscNameEnAsc().stream()
                        .map(ServiceItem::from)
                        .toList());
    }
}
