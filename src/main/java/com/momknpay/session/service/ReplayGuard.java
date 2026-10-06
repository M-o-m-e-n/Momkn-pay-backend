package com.momknpay.session.service;

import com.momknpay.common.config.AppProperties;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.util.TimeProvider;
import com.momknpay.session.domain.UsedNonce;
import com.momknpay.session.repository.UsedNonceRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rejects stale and replayed payloads (NFR-SEC-2…3): {@code ts} must be within the replay window of
 * server time, and each nonce is accepted once — enforced by the {@code used_nonces} primary key.
 */
@Component
public class ReplayGuard {

    private static final Logger log = LoggerFactory.getLogger(ReplayGuard.class);

    private final UsedNonceRepository nonces;
    private final TimeProvider time;
    private final long windowSeconds;

    public ReplayGuard(UsedNonceRepository nonces, TimeProvider time, AppProperties properties) {
        this.nonces = nonces;
        this.time = time;
        this.windowSeconds = properties.replayWindow().toSeconds();
    }

    /**
     * Runs in its own transaction so the nonce stays consumed even if the caller's business
     * transaction later rolls back: a payload is single-use whatever the outcome.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void check(String sessionId, String nonce, long ts) {
        Instant now = time.now();
        if (Math.abs(now.getEpochSecond() - ts) > windowSeconds) {
            log.warn("payload.rejected reason=stale_ts sessionId={}", sessionId);
            throw new ApiException(ErrorCode.DECRYPTION_FAILED);
        }
        try {
            nonces.saveAndFlush(new UsedNonce(nonce, sessionId, now));
        } catch (DataIntegrityViolationException e) {
            log.warn("payload.rejected reason=replayed_nonce sessionId={}", sessionId);
            throw new ApiException(ErrorCode.DECRYPTION_FAILED);
        }
    }
}
