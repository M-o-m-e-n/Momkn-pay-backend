package com.momknpay.payment.domain;

/** Mock outcome decided once, at inquiry time, from the subscriber number (SRS §6.1). */
public enum MockRule {
    /** Last digit 1–5: normal bill, confirm succeeds. */
    NORMAL,
    /** Last digit 6: bill above the service maximum, confirm is rejected. */
    LARGE,
    /** Last digit 7: confirm fails with INSUFFICIENT_BALANCE. */
    DECLINE,
    /** Last digit 8: confirm returns PENDING, then SUCCESS after a delay. */
    PENDING
}
