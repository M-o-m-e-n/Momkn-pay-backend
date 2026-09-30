package com.momknpay.transaction.service;

import com.momknpay.common.util.TimeProvider;
import com.momknpay.transaction.domain.Transaction;
import com.momknpay.transaction.domain.TransactionStatus;
import com.momknpay.transaction.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mock rule 8: a PENDING payment becomes SUCCESS once its delay (10 s) has passed (FR-TXN-5).
 *
 * <p>Resolution is lazy — every read path (receipt, history, idempotent replay) calls {@link
 * #resolveIfDue} so clients see SUCCESS the moment it is due — and a sweep also resolves rows
 * nobody reads. Both are idempotent, and {@code paidAt} is always {@code pendingUntil}, so the
 * result does not depend on which one got there first.
 */
@Component
public class PendingResolver {

    private static final Logger log = LoggerFactory.getLogger(PendingResolver.class);

    private final TransactionRepository transactions;
    private final TimeProvider time;

    public PendingResolver(TransactionRepository transactions, TimeProvider time) {
        this.transactions = transactions;
        this.time = time;
    }

    /** Call inside the caller's transaction; the managed entity is flushed on commit. */
    public Transaction resolveIfDue(Transaction transaction) {
        if (transaction.resolveIfDue(time.now())) {
            log.info("payment.pending_resolved txnId={}", transaction.getId());
        }
        return transaction;
    }

    @Scheduled(fixedDelayString = "PT5S", initialDelayString = "PT5S")
    @Transactional
    public int sweep() {
        int resolved =
                transactions.resolveDuePending(
                        time.now(), TransactionStatus.PENDING, TransactionStatus.SUCCESS);
        if (resolved > 0) {
            log.info("payment.pending_swept count={}", resolved);
        }
        return resolved;
    }
}
