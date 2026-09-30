package com.momknpay.transaction.web.dto;

import com.momknpay.common.error.ErrorCode;
import com.momknpay.transaction.domain.Transaction;
import com.momknpay.transaction.domain.TransactionStatus;
import java.time.Instant;

/** The full receipt: itemised amounts in piastres, snapshotted when the payment was made. */
public record ReceiptResponse(
        String transactionId,
        String inquiryId,
        TransactionStatus status,
        ErrorCode failureCode,
        String reference,
        ServiceSummary service,
        String subscriberNumber,
        String customerName,
        String billMonth,
        long amountDue,
        long serviceFee,
        long vat,
        long total,
        String currency,
        Instant createdAt,
        Instant paidAt) {

    public static ReceiptResponse from(Transaction transaction) {
        return new ReceiptResponse(
                transaction.getId(),
                transaction.getInquiryId(),
                transaction.getStatus(),
                TransactionItem.failureCode(transaction),
                transaction.getReference(),
                ServiceSummary.from(transaction.getService()),
                transaction.getSubscriberNumber(),
                transaction.getCustomerName(),
                transaction.getBillMonth(),
                transaction.getAmountDue(),
                transaction.getServiceFee(),
                transaction.getVat(),
                transaction.getTotal(),
                "EGP",
                transaction.getCreatedAt(),
                transaction.getPaidAt());
    }
}
