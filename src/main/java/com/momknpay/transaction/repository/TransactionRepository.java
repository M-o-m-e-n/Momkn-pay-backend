package com.momknpay.transaction.repository;

import com.momknpay.transaction.domain.Transaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TransactionRepository extends JpaRepository<Transaction, String> {

    /** The idempotency lookup: at most one row per (user, key) — ux_txn_idempotency. */
    Optional<Transaction> findByUserIdAndIdempotencyKey(String userId, UUID idempotencyKey);

    @Query(value = "select nextval('transaction_seq')", nativeQuery = true)
    long nextSeq();
}
