package com.momknpay.session.repository;

import com.momknpay.session.domain.Session;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SessionRepository extends JpaRepository<Session, String> {

    @Modifying
    @Query("delete from Session s where s.expiresAt < :cutoff")
    int deleteExpiredBefore(Instant cutoff);
}
