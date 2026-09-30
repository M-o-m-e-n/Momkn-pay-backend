package com.momknpay.payment.web.dto;

import com.momknpay.transaction.domain.Transaction;
import com.momknpay.transaction.domain.TransactionStatus;
import java.time.Instant;

/**
 * Result of a confirmation — identical for the first call and every replay of its key.
 *
 * @param paidAt null while {@code PENDING}
 */
public record ConfirmResponse(
        String transactionId,
        TransactionStatus status,
        String reference,
        Instant paidAt,
        long total,
        ServiceRef service) {

    public record ServiceRef(String id, String nameEn, String nameAr) {}

    /** Call inside the transaction: reads the lazy service. */
    public static ConfirmResponse from(Transaction transaction) {
        return new ConfirmResponse(
                transaction.getId(),
                transaction.getStatus(),
                transaction.getReference(),
                transaction.getPaidAt(),
                transaction.getTotal(),
                new ServiceRef(
                        transaction.getService().getId(),
                        transaction.getService().getNameEn(),
                        transaction.getService().getNameAr()));
    }
}
