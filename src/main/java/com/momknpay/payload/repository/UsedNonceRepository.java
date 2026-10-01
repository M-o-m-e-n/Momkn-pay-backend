package com.momknpay.payload.repository;

import com.momknpay.payload.domain.UsedNonce;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UsedNonceRepository extends JpaRepository<UsedNonce, String> {

    @Modifying
    @Query("delete from UsedNonce n where n.createdAt < :cutoff")
    int purgeBefore(Instant cutoff);
}
