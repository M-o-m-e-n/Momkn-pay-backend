package com.momknpay.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.momknpay.catalog.domain.BillerService;
import com.momknpay.common.util.TimeProvider;
import com.momknpay.transaction.domain.Transaction;
import com.momknpay.transaction.domain.TransactionStatus;
import com.momknpay.transaction.repository.TransactionRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** U17: PENDING becomes SUCCESS exactly when its delay has passed. */
class PendingResolverTest {

    private static final Instant CREATED = Instant.parse("2026-09-20T10:00:00Z");
    private static final Instant DUE = CREATED.plusSeconds(10);

    private final TimeProvider time = mock(TimeProvider.class);
    private final PendingResolver resolver =
            new PendingResolver(mock(TransactionRepository.class), time);

    @Test
    void staysPendingBeforeTheDelay() {
        when(time.now()).thenReturn(CREATED.plusSeconds(9));

        Transaction transaction = resolver.resolveIfDue(pending());

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(transaction.getPaidAt()).isNull();
    }

    @Test
    void becomesSuccessPaidAtTheDueTime() {
        when(time.now()).thenReturn(DUE.plusSeconds(50)); // read late

        Transaction transaction = resolver.resolveIfDue(pending());

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(transaction.getPaidAt()).isEqualTo(DUE); // not the read time
    }

    @Test
    void settledTransactionsAreLeftAlone() {
        when(time.now()).thenReturn(DUE.plusSeconds(1));
        Transaction failed = newTransaction();
        failed.fail("INSUFFICIENT_BALANCE");

        assertThat(resolver.resolveIfDue(failed).getStatus()).isEqualTo(TransactionStatus.FAILED);
    }

    private static Transaction pending() {
        Transaction transaction = newTransaction();
        transaction.pend(DUE);
        return transaction;
    }

    private static Transaction newTransaction() {
        return new Transaction(
                5501,
                "MP-20260920-5501",
                "usr_01",
                "inq_1",
                mock(BillerService.class),
                UUID.randomUUID(),
                "1024750898",
                "Heba F.",
                "2026-08",
                24_750,
                500,
                70,
                CREATED);
    }
}
