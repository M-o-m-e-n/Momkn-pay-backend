package com.momknpay.common.ratelimit;

import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;

/** {@code RATE_LIMITED}, carrying how long the client must wait (sent as {@code Retry-After}). */
public class RateLimitedException extends ApiException {

    private final long retryAfterSeconds;

    public RateLimitedException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
