package com.momknpay.payment.domain;

/** Stored inquiry state. Expiry is derived from {@code expiresAt}, never stored. */
public enum InquiryStatus {
    OPEN,
    CONFIRMED,
    INVALIDATED
}
