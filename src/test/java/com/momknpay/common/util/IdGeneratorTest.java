package com.momknpay.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

    private final IdGenerator ids = new IdGenerator(new SecureRandom());

    @Test
    void idsHaveThePrefixAndLengthOfTheContract() {
        assertThat(ids.sessionId()).matches("^ses_[0-9a-f]{32}$");
        assertThat(ids.inquiryId()).matches("^inq_[0-9a-f]{16}$");
    }

    @Test
    void idsAreNotRepeated() {
        assertThat(ids.sessionId()).isNotEqualTo(ids.sessionId());
    }

    @Test
    void timeProviderTruncatesToSeconds() {
        var clock = Clock.fixed(Instant.parse("2026-09-20T10:00:00.987654Z"), ZoneOffset.UTC);

        assertThat(new TimeProvider(clock).now()).isEqualTo(Instant.parse("2026-09-20T10:00:00Z"));
    }
}
