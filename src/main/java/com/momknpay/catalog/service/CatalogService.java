package com.momknpay.catalog.service;

import com.momknpay.catalog.repository.BillerServiceRepository;
import com.momknpay.catalog.web.dto.CatalogResponse;
import com.momknpay.catalog.web.dto.ServiceItem;
import com.momknpay.catalog.web.dto.SyncResponse;
import com.momknpay.common.util.TimeProvider;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The payable-services catalogue that clients cache for offline use (FR-CAT). */
@Service
public class CatalogService {

    /**
     * {@code updated_at} is stamped by a database trigger (DB clock) while {@code syncedAt} comes
     * from the application clock. Handing out a {@code syncedAt} slightly in the past absorbs a few
     * seconds of skew between the two: the next delta re-sends a few rows (clients upsert) instead
     * of missing one.
     */
    static final Duration SKEW_OVERLAP = Duration.ofSeconds(5);

    private final BillerServiceRepository services;
    private final TimeProvider time;

    public CatalogService(BillerServiceRepository services, TimeProvider time) {
        this.services = services;
        this.time = time;
    }

    /** FR-CAT-1: every non-deleted service, inactive ones included (shown disabled). */
    @Transactional(readOnly = true)
    public CatalogResponse getAll() {
        Instant syncedAt = syncPoint(); // captured before reading, see sync()
        return new CatalogResponse(
                syncedAt,
                services.findByDeletedAtIsNullOrderByCategoryAscNameEnAsc().stream()
                        .map(ServiceItem::from)
                        .toList());
    }

    /**
     * FR-CAT-4…5: rows created, updated or deleted at or after {@code since}.
     *
     * <p>{@code syncedAt} is taken <em>before</em> the queries and the filter is {@code >=}, so a
     * row written while this runs (or in the same second) is sent again next time instead of being
     * missed.
     */
    @Transactional(readOnly = true)
    public SyncResponse sync(Instant since) {
        Instant syncedAt = syncPoint();
        return new SyncResponse(
                syncedAt,
                services
                        .findByDeletedAtIsNullAndUpdatedAtGreaterThanEqualOrderByCategoryAscNameEnAsc(
                                since)
                        .stream()
                        .map(ServiceItem::from)
                        .toList(),
                services.findDeletedIdsSince(since));
    }

    private Instant syncPoint() {
        return time.now().minus(SKEW_OVERLAP);
    }
}
