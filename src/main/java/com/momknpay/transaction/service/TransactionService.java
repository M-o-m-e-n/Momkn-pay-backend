package com.momknpay.transaction.service;

import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.transaction.repository.TransactionRepository;
import com.momknpay.transaction.web.dto.PageResponse;
import com.momknpay.transaction.web.dto.ReceiptResponse;
import com.momknpay.transaction.web.dto.TransactionItem;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Payment history and receipts (FR-TXN). Due PENDING rows are resolved as they are read. */
@Service
public class TransactionService {

    /** Newest first; seq breaks ties between payments made in the same second. */
    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("seq"));

    private final TransactionRepository transactions;
    private final PendingResolver pendingResolver;

    public TransactionService(TransactionRepository transactions, PendingResolver pendingResolver) {
        this.transactions = transactions;
        this.pendingResolver = pendingResolver;
    }

    /** Read-write: resolving a due PENDING row is a write. */
    @Transactional
    public PageResponse<TransactionItem> list(String userId, int page, int size) {
        return PageResponse.of(
                transactions
                        .findByUserId(userId, PageRequest.of(page, size, NEWEST_FIRST))
                        .map(t -> TransactionItem.from(pendingResolver.resolveIfDue(t))));
    }

    /** Another user's transaction is indistinguishable from a missing one. */
    @Transactional
    public ReceiptResponse receipt(String userId, String transactionId) {
        return transactions
                .findByIdAndUserId(transactionId, userId)
                .map(pendingResolver::resolveIfDue)
                .map(ReceiptResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.TRANSACTION_NOT_FOUND));
    }
}
