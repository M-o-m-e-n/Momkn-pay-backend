package com.momknpay.payment.service;

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
import com.momknpay.transaction.service.ReferenceGenerator;
import com.momknpay.user.domain.User;
import com.momknpay.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pays an open inquiry with the PIN, exactly once per idempotency key (FR-PAY, LLD §9.4).
 *
 * <p>Order matters:
 *
 * <ol>
 *   <li>Look the key up <b>before decrypting</b>: a genuine retry resends the same bytes and nonce,
 *       which the replay guard would otherwise reject — the client would never get its receipt.
 *   <li>Lock the inquiry row, then look the key up again: two first-time requests with the same key
 *       queue on the lock and the second one replays the first one's transaction.
 *   <li>The {@code ux_txn_idempotency} unique constraint is the last line of defence; losing that
 *       race also replays.
 * </ol>
 *
 * The work runs in one database transaction that returns a {@link ConfirmOutcome}; the HTTP result
 * is produced after commit, and the {@code _slow} delay after that.
 */
@Service
public class ConfirmService {

    private static final Logger log = LoggerFactory.getLogger(ConfirmService.class);
    private static final Pattern PIN = Pattern.compile("^[0-9]{4}$");

    private final InquiryRepository inquiries;
    private final TransactionRepository transactions;
    private final UserRepository users;
    private final PayloadDecryptor decryptor;
    private final PasswordEncoder pinEncoder;
    private final SlowServiceDelay slowDelay;
    private final PendingResolver pendingResolver;
    private final TransactionTemplate tx;
    private final TimeProvider time;
    private final AppProperties properties;

    public ConfirmService(
            InquiryRepository inquiries,
            TransactionRepository transactions,
            UserRepository users,
            PayloadDecryptor decryptor,
            PasswordEncoder pinEncoder,
            SlowServiceDelay slowDelay,
            PendingResolver pendingResolver,
            TransactionTemplate tx,
            TimeProvider time,
            AppProperties properties) {
        this.inquiries = inquiries;
        this.transactions = transactions;
        this.users = users;
        this.decryptor = decryptor;
        this.pinEncoder = pinEncoder;
        this.slowDelay = slowDelay;
        this.pendingResolver = pendingResolver;
        this.tx = tx;
        this.time = time;
        this.properties = properties;
    }

    public ConfirmResponse confirm(
            String userId, String sessionId, UUID idempotencyKey, ConfirmRequest request) {
        AtomicBoolean slow = new AtomicBoolean(); // decided in the transaction, used after it
        try {
            ConfirmOutcome outcome;
            try {
                outcome =
                        tx.execute(
                                status ->
                                        confirmInTransaction(
                                                userId, sessionId, idempotencyKey, request, slow));
            } catch (DataIntegrityViolationException raceLost) {
                outcome =
                        tx.execute(
                                status ->
                                        findByKey(userId, idempotencyKey)
                                                .map(t -> replay(t, request.inquiryId(), slow))
                                                .orElseThrow(() -> raceLost));
            }
            return outcome.render();
        } finally {
            slowDelay.applyIf(slow.get()); // after commit: no lock or connection held
        }
    }

    private ConfirmOutcome confirmInTransaction(
            String userId,
            String sessionId,
            UUID idempotencyKey,
            ConfirmRequest request,
            AtomicBoolean slow) {
        // 1. replay path, before any decryption
        Optional<Transaction> existing = findByKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), request.inquiryId(), slow);
        }

        // 2. serialise confirmations of this inquiry, then re-check the key under the lock
        Inquiry inquiry =
                inquiries
                        .findForUpdate(request.inquiryId(), userId)
                        .orElseThrow(() -> new ApiException(ErrorCode.INQUIRY_NOT_FOUND));
        slow.set(inquiry.getService().isSlow());
        Optional<Transaction> again = findByKey(userId, idempotencyKey);
        if (again.isPresent()) {
            return replay(again.get(), request.inquiryId(), slow);
        }

        // 3. is this inquiry still payable?
        Instant now = time.now();
        if (inquiry.getStatus() == InquiryStatus.INVALIDATED) {
            return ConfirmOutcome.failed(ErrorCode.INQUIRY_INVALIDATED);
        }
        if (inquiry.getStatus() == InquiryStatus.CONFIRMED) {
            return ConfirmOutcome.failed(ErrorCode.INQUIRY_ALREADY_CONFIRMED);
        }
        if (inquiry.isExpired(now)) {
            return ConfirmOutcome.failed(ErrorCode.INQUIRY_EXPIRED);
        }
        BillerService biller = inquiry.getService();
        if (!biller.isActive()) {
            return ConfirmOutcome.failed(ErrorCode.SERVICE_UNAVAILABLE);
        }

        // 4. the PIN (decryption failures throw and roll back; the nonce stays consumed)
        ConfirmPayload payload =
                decryptor.decrypt(sessionId, userId, request.payload(), ConfirmPayload.class);
        if (payload.pin() == null || !PIN.matcher(payload.pin()).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "pin");
        }
        User user =
                users.findById(userId)
                        .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));
        if (!pinEncoder.matches(payload.pin(), user.getPinHash())) {
            inquiry.registerWrongPin(); // committed with the outcome; the third invalidates
            log.info(
                    "payment.pin_failed inquiryId={} attempts={}",
                    inquiry.getId(),
                    inquiry.getFailedPinAttempts());
            return ConfirmOutcome.failed(ErrorCode.VALIDATION_ERROR, "pin");
        }

        // 5. amount limits (mock rule 6)
        if (inquiry.getRule() == MockRule.LARGE
                || inquiry.getAmountDue() > biller.getMaxAmount()
                || inquiry.getAmountDue() < biller.getMinAmount()) {
            return ConfirmOutcome.failed(ErrorCode.AMOUNT_OUT_OF_RANGE);
        }

        // 6. the transaction, decided by the inquiry's mock rule
        Transaction transaction = newTransaction(inquiry, userId, idempotencyKey, now);
        switch (inquiry.getRule()) {
            case PENDING -> {
                transaction.pend(now.plus(properties.pendingDelay()));
                inquiry.markConfirmed();
            }
            case DECLINE -> transaction.fail(ErrorCode.INSUFFICIENT_BALANCE.name()); // stays open
            default -> {
                transaction.succeed(now);
                inquiry.markConfirmed();
            }
        }
        transactions.saveAndFlush(transaction); // constraint violations surface here
        log.info(
                "payment.confirmed txnId={} status={} inquiryId={}",
                transaction.getId(),
                transaction.getStatus(),
                inquiry.getId());
        return outcomeOf(transaction);
    }

    private ConfirmOutcome replay(Transaction transaction, String inquiryId, AtomicBoolean slow) {
        slow.set(transaction.getService().isSlow());
        if (!transaction.getInquiryId().equals(inquiryId)) {
            return ConfirmOutcome.failed(ErrorCode.IDEMPOTENCY_CONFLICT);
        }
        log.info("payment.replayed txnId={}", transaction.getId());
        return outcomeOf(pendingResolver.resolveIfDue(transaction)); // current status
    }

    private static ConfirmOutcome outcomeOf(Transaction transaction) {
        if (transaction.getStatus() == TransactionStatus.FAILED) {
            return ConfirmOutcome.failed(ErrorCode.valueOf(transaction.getFailureCode()));
        }
        return ConfirmOutcome.paid(ConfirmResponse.from(transaction));
    }

    private Transaction newTransaction(
            Inquiry inquiry, String userId, UUID idempotencyKey, Instant now) {
        long seq = transactions.nextSeq();
        return new Transaction(
                seq,
                ReferenceGenerator.of(now, seq),
                userId,
                inquiry.getId(),
                inquiry.getService(),
                idempotencyKey,
                inquiry.getSubscriberNumber(),
                inquiry.getCustomerName(),
                inquiry.getBillMonth(),
                inquiry.getAmountDue(),
                inquiry.getServiceFee(),
                inquiry.getVat(),
                now);
    }

    private Optional<Transaction> findByKey(String userId, UUID idempotencyKey) {
        return transactions.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }
}
