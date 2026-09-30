package com.momknpay.common.util;

/** Masks values before they reach a log line (CODING_STANDARDS §10.3). */
public final class Masking {

    private static final int VISIBLE = 4;

    private Masking() {}

    /** {@code 1024750891} → {@code ******0891}. */
    public static String subscriber(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= VISIBLE) {
            return "*".repeat(value.length());
        }
        return "*".repeat(value.length() - VISIBLE) + value.substring(value.length() - VISIBLE);
    }
}
