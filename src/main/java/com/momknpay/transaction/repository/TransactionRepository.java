package com.momknpay.transaction.repository;

import com.momknpay.transaction.domain.Transaction;
import com.momknpay.transaction.domain.TransactionStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /** The idempotency lookup: at most one row per (user, key) — ux_txn_idempotency. */
    Optional<Transaction> findByUserIdAndIdempotencyKey(String userId, UUID idempotencyKey);

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
