package com.momknpay.transaction.repository;

import com.momknpay.transaction.domain.Transaction;
import com.momknpay.transaction.domain.TransactionStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /** The idempotency lookup: at most one row per (user, key) — ux_txn_idempotency. */
    Optional<Transaction> findByUserIdAndIdempotencyKey(String userId, UUID idempotencyKey);

    /** History page; the service is fetched in the same query (no N+1). */
    @EntityGraph(attributePaths = "service")
    Page<Transaction> findByUserId(String userId, Pageable pageable);

    /** A receipt, only for its owner. */
    @EntityGraph(attributePaths = "service")
    Optional<Transaction> findByIdAndUserId(String id, String userId);

    @Query(value = "select nextval('transaction_seq')", nativeQuery = true)
    long nextSeq();

    /** Bulk PENDING → SUCCESS for every row whose delay has passed; paidAt = pendingUntil. */
    @Modifying
    @Query(
            """
            update Transaction t set t.status = :success, t.paidAt = t.pendingUntil
            where t.status = :pending and t.pendingUntil <= :now\
            """)
    int resolveDuePending(Instant now, TransactionStatus pending, TransactionStatus success);
}
