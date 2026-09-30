package com.momknpay.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/** The error envelope every non-2xx response uses (SRS §4.3). */
public record ErrorResponse(ErrorBody error) {

    @JsonInclude(JsonInclude.Include.ALWAYS) // "field": null is part of the contract
    public record ErrorBody(String code, String messageEn, String messageAr, String field) {}

    public static ErrorResponse of(ErrorCode code, String field) {
        return new ErrorResponse(
                new ErrorBody(code.name(), code.messageEn(), code.messageAr(), field));
    }
}
