package com.momknpay.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * All {@code app.*} settings in one typed place (CODING_STANDARDS §6). Startup fails if any is
 * missing or invalid.
 *
 * @param masterKey base64 of 32 bytes; wraps session keys at rest (never logged)
 * @param sessionTtl lifetime of a crypto session
 * @param inquiryTtl lifetime of a fees inquiry
 * @param replayWindow allowed difference between a payload's {@code ts} and server time
 * @param nonceRetention how long consumed nonces are kept (must exceed the replay window)
 * @param slowDelay delay for services whose id ends in {@code _slow}
 * @param pendingDelay time a PENDING payment takes to become SUCCESS
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank String masterKey,
        @NotNull Duration sessionTtl,
        @NotNull Duration inquiryTtl,
        @NotNull Duration replayWindow,
        @NotNull Duration nonceRetention,
        @NotNull Duration slowDelay,
        @NotNull Duration pendingDelay,
        @Valid @NotNull RateLimit rateLimit) {

    public record RateLimit(@Min(1) int perMinute) {}

    @Override
    public String toString() {
        return "AppProperties[masterKey=****, sessionTtl=%s, inquiryTtl=%s]"
                .formatted(sessionTtl, inquiryTtl);
    }
}
