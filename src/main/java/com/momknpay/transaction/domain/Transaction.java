package com.momknpay.transaction.domain;

import com.momknpay.catalog.domain.BillerService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A payment attempt. Snapshots the inquiry's amounts so a receipt never changes. Unique per (user,
 * idempotency key) — enforced by the database.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id private String id;

    @Column(nullable = false, updatable = false, unique = true)
    private long seq;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(name = "inquiry_id", nullable = false, updatable = false)
    private String inquiryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private BillerService service;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private UUID idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TransactionStatus status;

    @Column(name = "failure_code", length = 32)
    private String failureCode;

    @Column(nullable = false, updatable = false, unique = true, length = 32)
    private String reference;

    @Column(name = "subscriber_number", nullable = false, updatable = false, length = 32)
    private String subscriberNumber;

    @Column(name = "customer_name", nullable = false, updatable = false, length = 100)
    private String customerName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "bill_month", nullable = false, updatable = false, length = 7)
    private String billMonth;

    @Column(name = "amount_due", nullable = false, updatable = false)
    private long amountDue;

    @Column(name = "service_fee", nullable = false, updatable = false)
    private long serviceFee;

    @Column(nullable = false, updatable = false)
    private long vat;

    @Column(nullable = false, updatable = false)
    private long total;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "pending_until", updatable = false)
    private Instant pendingUntil;

    protected Transaction() {}

    /**
     * A new attempt snapshotting the inquiry's amounts. Exactly one of {@link #succeed}, {@link
     * #pend} or {@link #fail} must be called before it is saved.
     */
    public Transaction(
            long seq,
            String reference,
            String userId,
            String inquiryId,
            BillerService service,
            UUID idempotencyKey,
            String subscriberNumber,
            String customerName,
            String billMonth,
            long amountDue,
            long serviceFee,
            long vat,
            Instant createdAt) {
        this.id = "txn_" + seq;
        this.seq = seq;
        this.reference = reference;
        this.userId = userId;
        this.inquiryId = inquiryId;
        this.service = service;
        this.idempotencyKey = idempotencyKey;
        this.subscriberNumber = subscriberNumber;
        this.customerName = customerName;
        this.billMonth = billMonth;
        this.amountDue = amountDue;
        this.serviceFee = serviceFee;
        this.vat = vat;
        this.total = Math.addExact(Math.addExact(amountDue, serviceFee), vat);
        this.createdAt = createdAt;
    }

    public void succeed(Instant paidAt) {
        this.status = TransactionStatus.SUCCESS;
        this.paidAt = paidAt;
    }

    public void pend(Instant pendingUntil) {
        this.status = TransactionStatus.PENDING;
        this.pendingUntil = pendingUntil;
    }

    public void fail(String failureCode) {
        this.status = TransactionStatus.FAILED;
        this.failureCode = failureCode;
    }

    /**
     * PENDING → SUCCESS once {@code pendingUntil} has passed. {@code paidAt} is the moment the
     * transaction was due, so it does not depend on when it happens to be read.
     *
     * @return whether the status changed
     */
    public boolean resolveIfDue(Instant now) {
        if (status == TransactionStatus.PENDING && !pendingUntil.isAfter(now)) {
            status = TransactionStatus.SUCCESS;
            paidAt = pendingUntil;
            return true;
        }
        return false;
    }

    public String getId() {
        return id;
    }

    public long getSeq() {
        return seq;
    }

    public String getUserId() {
        return userId;
    }

    public String getInquiryId() {
        return inquiryId;
    }

    public BillerService getService() {
        return service;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getReference() {
        return reference;
    }

    public String getSubscriberNumber() {
        return subscriberNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getBillMonth() {
        return billMonth;
    }

    public long getAmountDue() {
        return amountDue;
    }

    public long getServiceFee() {
        return serviceFee;
    }

    public long getVat() {
        return vat;
    }

    public long getTotal() {
        return total;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getPendingUntil() {
        return pendingUntil;
    }
}
