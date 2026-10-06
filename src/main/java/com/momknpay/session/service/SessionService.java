package com.momknpay.session.service;

import com.momknpay.common.config.AppProperties;
import com.momknpay.common.crypto.AesGcmCipher;
import com.momknpay.common.crypto.KeyWrapper;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.util.IdGenerator;
import com.momknpay.common.util.TimeProvider;
import com.momknpay.session.domain.Session;
import com.momknpay.session.repository.SessionRepository;
import com.momknpay.session.web.dto.CreateSessionResponse;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues and revokes crypto sessions — the AES key that encrypts PIN and subscriber number. */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessions;
    private final KeyWrapper keyWrapper;
    private final IdGenerator ids;
    private final TimeProvider time;
    private final SecureRandom random;
    private final AppProperties properties;

    public SessionService(
            SessionRepository sessions,
            KeyWrapper keyWrapper,
            IdGenerator ids,
            TimeProvider time,
            SecureRandom random,
            AppProperties properties) {
        this.sessions = sessions;
        this.keyWrapper = keyWrapper;
        this.ids = ids;
        this.time = time;
        this.random = random;
        this.properties = properties;
    }

    /** FR-SES-1…3: the raw key is returned once and stored only wrapped. */
    @Transactional
    public CreateSessionResponse create(String userId) {
        byte[] key = new byte[AesGcmCipher.KEY_LENGTH];
        random.nextBytes(key);
        try {
            String sessionId = ids.sessionId();
            Instant now = time.now();
            Instant expiresAt = now.plus(properties.sessionTtl());
            sessions.save(
                    new Session(
                            sessionId, userId, keyWrapper.wrap(sessionId, key), now, expiresAt));
            log.info("session.created sessionId={} userId={}", sessionId, userId);
            return new CreateSessionResponse(
                    sessionId, Base64.getEncoder().encodeToString(key), expiresAt);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /** FR-SES-5: idempotent; a foreign or unknown session is SESSION_NOT_FOUND. */
    @Transactional
    public void revoke(String userId, String sessionId) {
        Session session =
                sessions.findById(sessionId)
                        .filter(candidate -> candidate.ownedBy(userId))
                        .orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
        session.revoke(time.now());
        log.info("session.revoked sessionId={} userId={}", sessionId, userId);
    }
}
