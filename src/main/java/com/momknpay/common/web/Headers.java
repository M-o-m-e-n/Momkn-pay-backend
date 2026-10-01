package com.momknpay.common.web;

/** Custom HTTP header names of the contract (SRS §4.2). */
public final class Headers {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String CLIENT_PLATFORM = "X-Client-Platform";
    public static final String CLIENT_VERSION = "X-Client-Version";
    public static final String USER_ID = "X-User-Id";
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String RETRY_AFTER = "Retry-After";

    private Headers() {}
}
