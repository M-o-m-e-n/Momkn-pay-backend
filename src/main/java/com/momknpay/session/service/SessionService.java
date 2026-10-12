package com.momknpay.session.service;

import com.momknpay.common.config.AppProperties;
import com.momknpay.common.crypto.CryptoException;
import com.momknpay.common.crypto.KeyWrapper;
import com.momknpay.common.crypto.ServerKeyPair;
import com.momknpay.common.crypto.SessionKeyAgreement;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.util.IdGenerator;
import com.momknpay.common.util.TimeProvider;
import com.momknpay.session.domain.Session;
import com.momknpay.session.repository.SessionRepository;
import com.momknpay.session.web.dto.CreateSessionResponse;
import com.momknpay.session.web.dto.ServerPublicKeyResponse;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens and revokes crypto sessions. The AES key that encrypts PIN and subscriber number is agreed
 * with the client, never sent (ADR-013).
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private static final String KEY_ALGORITHM = "ECDH-P256";

    private final SessionRepository sessions;
    private final KeyWrapper keyWrapper;
    private final IdGenerator ids;
    private final TimeProvider time;
    private final SessionKeyAgreement keyAgreement;
    private final ServerKeyPair serverKeyPair;
    private final AppProperties properties;

    public SessionService(
            SessionRepository sessions,
            KeyWrapper keyWrapper,
            IdGenerator ids,
            TimeProvider time,
            SessionKeyAgreement keyAgreement,
            ServerKeyPair serverKeyPair,
            AppProperties properties) {
        this.sessions = sessions;
        this.keyWrapper = keyWrapper;
        this.ids = ids;
        this.time = time;
        this.keyAgreement = keyAgreement;
        this.serverKeyPair = serverKeyPair;
        this.properties = properties;
    }

    /** The server's long-term handshake key, for clients that want to check their built-in copy. */
    public ServerPublicKeyResponse publicKey() {
        return new ServerPublicKeyResponse(
                serverKeyPair.keyId(), KEY_ALGORITHM, serverKeyPair.publicKeyBase64());
    }

    /** FR-SES-1…3: the key is derived on both sides and stored only wrapped. */
    @Transactional
    public CreateSessionResponse create(String userId, String clientPublicKey) {
        ECPublicKey clientKey = parseClientKey(clientPublicKey);
        String sessionId = ids.sessionId();
        SessionKeyAgreement.Handshake handshake =
                keyAgreement.respond(
                        serverKeyPair.privateKey(), clientKey, clientPublicKey, sessionId, userId);
        try {
            Instant now = time.now();
            Instant expiresAt = now.plus(properties.sessionTtl());
            sessions.save(
                    new Session(
                            sessionId,
                            userId,
                            keyWrapper.wrap(sessionId, handshake.sessionKey()),
                            now,
                            expiresAt));
            log.info("session.created sessionId={} userId={}", sessionId, userId);
            return new CreateSessionResponse(sessionId, handshake.serverPublicKey(), expiresAt);
        } finally {
            Arrays.fill(handshake.sessionKey(), (byte) 0);
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

    private ECPublicKey parseClientKey(String clientPublicKey) {
        try {
            return keyAgreement.parsePublicKey(clientPublicKey);
        } catch (CryptoException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "clientPublicKey");
        }
    }
}
