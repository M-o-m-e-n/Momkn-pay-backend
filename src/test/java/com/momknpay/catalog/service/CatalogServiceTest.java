package com.momknpay.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.momknpay.catalog.repository.BillerServiceRepository;
import com.momknpay.catalog.web.dto.SyncResponse;
import com.momknpay.common.util.TimeProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class CatalogServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final Instant SINCE = Instant.parse("2026-09-19T00:00:00Z");

    private final BillerServiceRepository repository = mock(BillerServiceRepository.class);
    private final TimeProvider time = mock(TimeProvider.class);
    private final CatalogService service = new CatalogService(repository, time);

    @Test
    void syncedAtIsTakenBeforeTheQueriesRunAndLeansIntoThePast() {
        when(time.now()).thenReturn(NOW);
        when(repository.findDeletedIdsSince(SINCE)).thenReturn(List.of("svc_water_legacy"));

        SyncResponse response = service.sync(SINCE);

        InOrder order = inOrder(time, repository);
        order.verify(time).now();
        order.verify(repository)
                .findByDeletedAtIsNullAndUpdatedAtGreaterThanEqualOrderByCategoryAscNameEnAsc(
                        SINCE);
        order.verify(repository).findDeletedIdsSince(SINCE);
        assertThat(response.syncedAt()).isEqualTo(NOW.minus(CatalogService.SKEW_OVERLAP));
        assertThat(response.items()).isEmpty();
        assertThat(response.deletedIds()).containsExactly("svc_water_legacy");
    }
}
