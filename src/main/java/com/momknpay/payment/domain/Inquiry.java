package com.momknpay.payment.domain;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A server-computed quote for one subscriber at one service, valid until {@code expiresAt}. */
@Entity
@Table(name = "inquiries")
public class Inquiry {

    public static final int MAX_WRONG_PINS = 3;

    @Id private String id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private BillerService service;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private MockRule rule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InquiryStatus status;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "failed_pin_attempts", nullable = false)
    private int failedPinAttempts;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected Inquiry() {}

    public Inquiry(
            String id,
            String userId,
            BillerService service,
            String subscriberNumber,
            String customerName,
            String billMonth,
            long amountDue,
            long serviceFee,
            long vat,
            MockRule rule,
            Instant createdAt,
            Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.service = service;
        this.subscriberNumber = subscriberNumber;
        this.customerName = customerName;
        this.billMonth = billMonth;
        this.amountDue = amountDue;
        this.serviceFee = serviceFee;
        this.vat = vat;
        this.total = Math.addExact(Math.addExact(amountDue, serviceFee), vat);
        this.rule = rule;
        this.status = InquiryStatus.OPEN;
        this.failedPinAttempts = 0;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    /** Counts a wrong PIN; the third one invalidates the inquiry. */
    public void registerWrongPin() {
        failedPinAttempts++;
        if (failedPinAttempts >= MAX_WRONG_PINS) {
            status = InquiryStatus.INVALIDATED;
        }
    }

    public void markConfirmed() {
        status = InquiryStatus.CONFIRMED;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public BillerService getService() {
        return service;
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

    public MockRule getRule() {
        return rule;
    }

    public InquiryStatus getStatus() {
        return status;
    }

    public int getFailedPinAttempts() {
        return failedPinAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
