package com.momknpay.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.momknpay.common.config.AppProperties;
import com.momknpay.common.ratelimit.RateLimiter.Policy;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final RateLimiter limiter = new RateLimiter(properties(5));

    @Test
    void sixthAttemptWithinAMinuteIsRateLimited() {
        for (int i = 0; i < 5; i++) {
            limiter.consume(Policy.CONFIRM, "usr_01");
        }

        assertThatThrownBy(() -> limiter.consume(Policy.CONFIRM, "usr_01"))
                .isInstanceOfSatisfying(
                        RateLimitedException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isBetween(1L, 60L));
    }

    @Test
    void bucketsAreSeparatePerUser() {
        for (int i = 0; i < 5; i++) {
            limiter.consume(Policy.CONFIRM, "usr_01");
        }

        assertThatCode(() -> limiter.consume(Policy.CONFIRM, "usr_02")).doesNotThrowAnyException();
    }

    private static AppProperties properties(int perMinute) {
        Duration any = Duration.ofMinutes(5);
        return new AppProperties(
                "unused", any, any, any, any, any, new AppProperties.RateLimit(perMinute));
    }
}
