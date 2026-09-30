package com.momknpay.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.momknpay.catalog.domain.BillerService;
import com.momknpay.common.config.AppProperties;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.util.TimeProvider;
import com.momknpay.payment.domain.Inquiry;
import com.momknpay.payment.domain.InquiryStatus;
import com.momknpay.payment.domain.MockRule;
import com.momknpay.payment.repository.InquiryRepository;
import com.momknpay.payment.web.dto.ConfirmPayload;
import com.momknpay.payment.web.dto.ConfirmRequest;
import com.momknpay.payment.web.dto.ConfirmResponse;
import com.momknpay.session.service.PayloadDecryptor;
import com.momknpay.transaction.domain.Transaction;
import com.momknpay.transaction.domain.TransactionStatus;
import com.momknpay.transaction.repository.TransactionRepository;
import com.momknpay.transaction.service.PendingResolver;
import com.momknpay.user.domain.User;
import com.momknpay.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** U11–U16: every outcome of a confirmation, decided with a fixed clock and no database. */
class ConfirmServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
    private static final String USER = "usr_01";
    private static final String SESSION = "ses_1";
    private static final String GOOD_PIN = "1234";

    private final InquiryRepository inquiries = mock(InquiryRepository.class);
    private final TransactionRepository transactions = mock(TransactionRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PayloadDecryptor decryptor = mock(PayloadDecryptor.class);
    private final PasswordEncoder pinEncoder = mock(PasswordEncoder.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final TimeProvider time = mock(TimeProvider.class);
    private final BillerService cairo = mock(BillerService.class);

    private final ConfirmService service =
            new ConfirmService(
                    inquiries,
                    transactions,
                    users,
                    decryptor,
                    pinEncoder,
                    mock(SlowServiceDelay.class),
                    new PendingResolver(transactions, time),
                    tx,
                    time,
                    new AppProperties(
                            "unused",
                            Duration.ofMinutes(30),
                            Duration.ofMinutes(5),
                            Duration.ofSeconds(120),
                            Duration.ofMinutes(5),
                            Duration.ofSeconds(8),
                            Duration.ofSeconds(10),
                            new AppProperties.RateLimit(5)));

    @BeforeEach
    @SuppressWarnings("unchecked")
    void wire() {
        when(tx.execute(any()))
                .thenAnswer(
                        call ->
                                call.<TransactionCallback<Object>>getArgument(0)
                                        .doInTransaction(
                                                mock(
                                                        org.springframework.transaction
                                                                .TransactionStatus.class)));
        when(time.now()).thenReturn(NOW);
        when(cairo.getId()).thenReturn("svc_elec_cairo");
        when(cairo.isActive()).thenReturn(true);
        when(cairo.getMinAmount()).thenReturn(500L);
        when(cairo.getMaxAmount()).thenReturn(500_000L);
        when(users.findById(USER))
                .thenReturn(
                        Optional.of(
                                new User(USER, "Mina Adel", "01000000001", "m@x.io", "hash", NOW)));
        when(pinEncoder.matches(GOOD_PIN, "hash")).thenReturn(true);
        when(transactions.nextSeq()).thenReturn(5600L);
        when(transactions.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(transactions.findByUserIdAndIdempotencyKey(eq(USER), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void normalRuleSucceedsAndConfirmsTheInquiry() {
        Inquiry inquiry = inquiry(MockRule.NORMAL, 24_750, NOW.plusSeconds(60));

        ConfirmResponse response = confirm(inquiry, GOOD_PIN);

        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.reference()).isEqualTo("MP-20260920-5600");
        assertThat(response.total()).isEqualTo(25_320);
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.CONFIRMED);
    }

    @Test
    void expiredInquiryCannotBePaid() { // U11
        Inquiry inquiry = inquiry(MockRule.NORMAL, 24_750, NOW.minusSeconds(1));

        assertFails(() -> confirm(inquiry, GOOD_PIN), ErrorCode.INQUIRY_EXPIRED, null);
        verify(decryptor, never()).decrypt(anyString(), anyString(), anyString(), any());
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void threeWrongPinsInvalidateTheInquiry() { // U14
        Inquiry inquiry = inquiry(MockRule.NORMAL, 24_750, NOW.plusSeconds(60));

        for (int attempt = 1; attempt <= 3; attempt++) {
            assertFails(() -> confirm(inquiry, "0000"), ErrorCode.VALIDATION_ERROR, "pin");
            assertThat(inquiry.getFailedPinAttempts()).isEqualTo(attempt);
        }
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.INVALIDATED);

        assertFails(() -> confirm(inquiry, GOOD_PIN), ErrorCode.INQUIRY_INVALIDATED, null);
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void malformedPinIsRejectedWithoutCountingAnAttempt() {
        Inquiry inquiry = inquiry(MockRule.NORMAL, 24_750, NOW.plusSeconds(60));

        assertFails(() -> confirm(inquiry, "12a4"), ErrorCode.VALIDATION_ERROR, "pin");
        assertThat(inquiry.getFailedPinAttempts()).isZero();
    }

    @Test
    void largeBillIsRejectedWithoutATransaction() { // U15
        Inquiry inquiry = inquiry(MockRule.LARGE, 510_000, NOW.plusSeconds(60));

        assertFails(() -> confirm(inquiry, GOOD_PIN), ErrorCode.AMOUNT_OUT_OF_RANGE, null);
        verify(transactions, never()).saveAndFlush(any());
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.OPEN);
    }

    @Test
    void declineRecordsAFailedTransactionAndKeepsTheInquiryOpen() { // U16
        Inquiry inquiry = inquiry(MockRule.DECLINE, 24_750, NOW.plusSeconds(60));

        assertFails(() -> confirm(inquiry, GOOD_PIN), ErrorCode.INSUFFICIENT_BALANCE, null);

        Transaction saved = savedTransaction();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(saved.getFailureCode()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(saved.getPaidAt()).isNull();
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.OPEN);
    }

    @Test
    void pendingRuleCreatesAPendingTransactionDueInTenSeconds() {
        Inquiry inquiry = inquiry(MockRule.PENDING, 24_750, NOW.plusSeconds(60));

        ConfirmResponse response = confirm(inquiry, GOOD_PIN);

        assertThat(response.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(response.paidAt()).isNull();
        assertThat(savedTransaction().getPendingUntil()).isEqualTo(NOW.plusSeconds(10));
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.CONFIRMED);
    }

    @Test
    void inactiveServiceIsUnavailable() {
        Inquiry inquiry = inquiry(MockRule.NORMAL, 24_750, NOW.plusSeconds(60));
        when(cairo.isActive()).thenReturn(false);

        assertFails(() -> confirm(inquiry, GOOD_PIN), ErrorCode.SERVICE_UNAVAILABLE, null);
    }

    @Test
    void replayReturnsTheStoredTransactionWithoutDecrypting() { // U12
        Inquiry inquiry = inquiry(MockRule.NORMAL, 24_750, NOW.plusSeconds(60));
        UUID key = UUID.randomUUID();
        when(transactions.findByUserIdAndIdempotencyKey(USER, key))
                .thenReturn(Optional.of(storedTransaction(inquiry.getId(), key)));

        ConfirmResponse response =
                service.confirm(USER, SESSION, key, new ConfirmRequest(inquiry.getId(), "blob"));

        assertThat(response.transactionId()).isEqualTo("txn_5500");
        verify(decryptor, never()).decrypt(anyString(), anyString(), anyString(), any());
        verify(inquiries, never()).findForUpdate(anyString(), anyString());
    }

    @Test
    void keyReusedForAnotherInquiryIsAConflict() { // U13
        UUID key = UUID.randomUUID();
        when(transactions.findByUserIdAndIdempotencyKey(USER, key))
                .thenReturn(Optional.of(storedTransaction("inq_first", key)));

        assertFails(
                () -> service.confirm(USER, SESSION, key, new ConfirmRequest("inq_other", "blob")),
                ErrorCode.IDEMPOTENCY_CONFLICT,
                null);
    }

    // ---- helpers ---------------------------------------------------------------------------

    private Inquiry inquiry(MockRule rule, long amountDue, Instant expiresAt) {
        long fee = Math.max(500, Math.ceilDiv(amountDue, 200));
        long vat = Math.ceilDiv(fee * 14, 100);
        Inquiry inquiry =
                new Inquiry(
                        "inq_test",
                        USER,
                        cairo,
                        SESSION,
                        "1024750891",
                        "Mina A.",
                        "2026-08",
                        amountDue,
                        fee,
                        vat,
                        rule,
                        NOW.minusSeconds(30),
                        expiresAt);
        when(inquiries.findForUpdate("inq_test", USER)).thenReturn(Optional.of(inquiry));
        return inquiry;
    }

    private ConfirmResponse confirm(Inquiry inquiry, String pin) {
        when(decryptor.decrypt(eq(SESSION), eq(USER), eq("blob"), eq(ConfirmPayload.class)))
                .thenReturn(new ConfirmPayload(pin, "0".repeat(32), NOW.getEpochSecond()));
        return service.confirm(
                USER, SESSION, UUID.randomUUID(), new ConfirmRequest(inquiry.getId(), "blob"));
    }

    private Transaction storedTransaction(String inquiryId, UUID key) {
        Transaction transaction =
                new Transaction(
                        5500,
                        "MP-20260920-5500",
                        USER,
                        inquiryId,
                        cairo,
                        key,
                        "1024750891",
                        "Mina A.",
                        "2026-08",
                        24_750,
                        500,
                        70,
                        NOW);
        transaction.succeed(NOW);
        return transaction;
    }

    private Transaction savedTransaction() {
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactions).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static void assertFails(Runnable call, ErrorCode code, String field) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ApiException.class,
                        e -> {
                            assertThat(e.getCode()).isEqualTo(code);
                            assertThat(e.getField()).isEqualTo(field);
                        });
    }
}
