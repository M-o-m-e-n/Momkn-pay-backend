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
 * @param payloadKey base64 of 32 bytes; the static AES-256 key shared with the apps (ADR-011),
 *     never logged
 * @param inquiryTtl lifetime of a fees inquiry
 * @param replayWindow allowed difference between a payload's {@code ts} and server time
 * @param nonceRetention how long consumed nonces are kept (must exceed the replay window)
 * @param slowDelay delay for services whose id ends in {@code _slow}
 * @param pendingDelay time a PENDING payment takes to become SUCCESS
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotBlank String payloadKey,
        @NotNull Duration inquiryTtl,
        @NotNull Duration replayWindow,
        @NotNull Duration nonceRetention,
        @NotNull Duration slowDelay,
        @NotNull Duration pendingDelay,
        @Valid @NotNull RateLimit rateLimit) {

    public record RateLimit(@Min(1) int perMinute) {}

    @Override
    public String toString() {
        return "AppProperties[payloadKey=****, inquiryTtl=%s]".formatted(inquiryTtl);
    }
}
