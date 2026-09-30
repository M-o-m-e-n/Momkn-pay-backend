package com.momknpay.catalog.repository;

import com.momknpay.catalog.domain.BillerService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BillerServiceRepository extends JpaRepository<BillerService, String> {

    /** Visible catalogue: everything not soft-deleted, inactive services included. */
    List<BillerService> findByDeletedAtIsNullOrderByCategoryAscNameEnAsc();

    /** Created or updated at or after {@code since} (at-least-once delta, LLD §6.6). */
    List<BillerService>
            findByDeletedAtIsNullAndUpdatedAtGreaterThanEqualOrderByCategoryAscNameEnAsc(
                    Instant since);

    @Query("select s.id from BillerService s where s.deletedAt >= :since order by s.id")
    List<String> findDeletedIdsSince(Instant since);

    Optional<BillerService> findByIdAndDeletedAtIsNull(String id);
}
