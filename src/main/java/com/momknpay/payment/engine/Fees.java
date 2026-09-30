package com.momknpay.payment.engine;

/** Itemised bill in piastres. {@code total = amountDue + serviceFee + vat}. */
public record Fees(long amountDue, long serviceFee, long vat, long total) {}
