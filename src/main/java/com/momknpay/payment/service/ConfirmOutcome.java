package com.momknpay.payment.service;

import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.payment.web.dto.ConfirmResponse;

/**
 * What a confirmation produced, decided inside the database transaction and turned into an HTTP
 * result only after it committed. That is how outcomes that must both persist state and answer with
 * an error (a wrong-PIN count, a recorded decline) keep their writes.
 */
record ConfirmOutcome(ConfirmResponse response, ErrorCode error, String field) {

    static ConfirmOutcome paid(ConfirmResponse response) {
        return new ConfirmOutcome(response, null, null);
    }

    static ConfirmOutcome failed(ErrorCode error) {
        return new ConfirmOutcome(null, error, null);
    }

    static ConfirmOutcome failed(ErrorCode error, String field) {
        return new ConfirmOutcome(null, error, field);
    }

    /** 200 with the response, or the error envelope. */
    ConfirmResponse render() {
        if (error != null) {
            throw new ApiException(error, field);
        }
        return response;
    }
}
