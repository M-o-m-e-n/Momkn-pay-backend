package com.momknpay.common.error;

/** The only way business code signals an error. Turned into the envelope by the global handler. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final String field;

    public ApiException(ErrorCode code) {
        this(code, null);
    }

    /**
     * @param field the offending input (body property, query parameter or header name), or null
     */
    public ApiException(ErrorCode code, String field) {
        super(code.name(), null, false, false); // expected outcome: no stack trace needed
        this.code = code;
        this.field = field;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getField() {
        return field;
    }
}
