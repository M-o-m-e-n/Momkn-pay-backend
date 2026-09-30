package com.momknpay.transaction.web.dto;

import com.momknpay.common.error.ErrorCode;
import com.momknpay.transaction.domain.Transaction;
import com.momknpay.transaction.domain.TransactionStatus;
import java.time.Instant;

/**
 * One row of the Activity list.
 *
 * @param failureCode set only when {@code status} is FAILED
 */
public record TransactionItem(
        String transactionId,
        TransactionStatus status,
        String reference,
        long total,
        Instant createdAt,
        Instant paidAt,
        ErrorCode failureCode,
        ServiceSummary service) {

    public static TransactionItem from(Transaction transaction) {
        return new TransactionItem(
                transaction.getId(),
                transaction.getStatus(),
                transaction.getReference(),
                transaction.getTotal(),
                transaction.getCreatedAt(),
                transaction.getPaidAt(),
                failureCode(transaction),
                ServiceSummary.from(transaction.getService()));
    }

    static ErrorCode failureCode(Transaction transaction) {
        return transaction.getFailureCode() == null
                ? null
                : ErrorCode.valueOf(transaction.getFailureCode());
    }
}
