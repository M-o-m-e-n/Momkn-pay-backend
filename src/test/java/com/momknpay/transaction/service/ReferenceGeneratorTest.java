package com.momknpay.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** U19: the receipt reference format. */
class ReferenceGeneratorTest {

    @Test
    void referenceIsDateAndSequence() {
        assertThat(ReferenceGenerator.of(Instant.parse("2026-09-20T10:02:11Z"), 5521))
                .isEqualTo("MP-20260920-5521");
    }

    @Test
    void dateIsUtcAndShortSequencesArePadded() {
        assertThat(ReferenceGenerator.of(Instant.parse("2026-09-20T23:30:00Z"), 42))
                .isEqualTo("MP-20260920-0042");
        assertThat(ReferenceGenerator.of(Instant.parse("2026-09-20T23:30:00Z"), 123456))
                .isEqualTo("MP-20260920-123456");
    }
}
