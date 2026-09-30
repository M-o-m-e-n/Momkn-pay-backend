package com.momknpay.transaction.service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Human-readable receipt reference {@code MP-YYYYMMDD-NNNN} (unique because seq is). */
public final class ReferenceGenerator {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("uuuuMMdd")
                    .withZone(ZoneOffset.UTC); // BASIC_ISO_DATE would append "Z"

    private ReferenceGenerator() {}

    /** 2026-09-20, seq 5521 → {@code MP-20260920-5521}. */
    public static String of(Instant createdAt, long seq) {
        return "MP-" + DATE.format(createdAt) + "-" + String.format("%04d", seq);
    }
}
