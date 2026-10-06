package com.momknpay.session.service;

import com.momknpay.common.config.AppProperties;
import com.momknpay.common.util.TimeProvider;
import com.momknpay.session.repository.UsedNonceRepository;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgets nonces older than the retention (5 min). Safe because the retention is longer than the
 * replay window: an older payload is already rejected for its {@code ts}.
 */
@Component
public class NonceCleanupJob {

    private final UsedNonceRepository nonces;
    private final TimeProvider time;
    private final Duration retention;

    public NonceCleanupJob(
            UsedNonceRepository nonces, TimeProvider time, AppProperties properties) {
        if (properties.nonceRetention().compareTo(properties.replayWindow()) <= 0) {
            throw new IllegalStateException("app.nonce-retention must exceed app.replay-window");
        }
        this.nonces = nonces;
        this.time = time;
        this.retention = properties.nonceRetention();
    }

    @Scheduled(fixedDelayString = "PT60S", initialDelayString = "PT60S")
    @Transactional
    public int purge() {
        return nonces.purgeBefore(time.now().minus(retention));
    }
}
