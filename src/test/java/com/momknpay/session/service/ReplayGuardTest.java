package com.momknpay.session.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.momknpay.common.config.AppProperties;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.util.TimeProvider;
import com.momknpay.session.domain.UsedNonce;
import com.momknpay.session.repository.UsedNonceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** U4–U5: replayed nonces and stale timestamps are rejected (NFR-SEC-2). */
class ReplayGuardTest {

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");
    private static final String NONCE = "7f3a9c0e1b2d4f6a8c0e2b4d6f8a0c1e";

    private final UsedNonceRepository nonces = mock(UsedNonceRepository.class);
    private final ReplayGuard guard =
            new ReplayGuard(
                    nonces,
                    new TimeProvider(Clock.fixed(NOW, ZoneOffset.UTC)),
                    properties(Duration.ofSeconds(120)));

    @Test
    void reusedNonceIsRejected() {
        when(nonces.saveAndFlush(any(UsedNonce.class)))
                .thenReturn(null)
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        guard.check("ses_1", NONCE, NOW.getEpochSecond());

        assertDecryptionFailed(() -> guard.check("ses_1", NONCE, NOW.getEpochSecond()));
    }

    @Test
    void timestampsInsideTheWindowAreAccepted() {
        long now = NOW.getEpochSecond();

        assertThatCode(() -> guard.check("ses_1", NONCE, now - 120)).doesNotThrowAnyException();
        assertThatCode(() -> guard.check("ses_1", NONCE, now + 120)).doesNotThrowAnyException();
    }

    @Test
    void staleOrFutureTimestampIsRejectedBeforeTouchingTheDatabase() {
        long now = NOW.getEpochSecond();

        assertDecryptionFailed(() -> guard.check("ses_1", NONCE, now - 121));
        assertDecryptionFailed(() -> guard.check("ses_1", NONCE, now + 121));
        verify(nonces, never()).saveAndFlush(any());
    }

    private static void assertDecryptionFailed(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.DECRYPTION_FAILED);
    }

    static AppProperties properties(Duration replayWindow) {
        Duration any = Duration.ofMinutes(5);
        return new AppProperties(
                "unused", any, any, replayWindow, any, any, any, new AppProperties.RateLimit(5));
    }
}
