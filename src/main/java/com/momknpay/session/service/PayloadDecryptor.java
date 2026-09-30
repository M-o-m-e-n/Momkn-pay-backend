package com.momknpay.session.service;

import com.momknpay.common.crypto.AesGcmCipher;
import com.momknpay.common.crypto.CryptoException;
import com.momknpay.common.crypto.KeyWrapper;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.common.util.TimeProvider;
import com.momknpay.session.domain.Session;
import com.momknpay.session.repository.SessionRepository;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a {@code payload} string into its plaintext object (LLD §8.4): session checks, AES-GCM
 * decryption with the session key, JSON parsing, then the replay guard. Nothing it handles is ever
 * logged.
 */
@Component
public class PayloadDecryptor {

    private static final Pattern NONCE = Pattern.compile("^[0-9a-f]{32}$");

    private final SessionRepository sessions;
    private final KeyWrapper keyWrapper;
    private final AesGcmCipher cipher;
    private final ReplayGuard replayGuard;
    private final ObjectMapper objectMapper;
    private final TimeProvider time;

    public PayloadDecryptor(
            SessionRepository sessions,
            KeyWrapper keyWrapper,
            AesGcmCipher cipher,
            ReplayGuard replayGuard,
            ObjectMapper objectMapper,
            TimeProvider time) {
        this.sessions = sessions;
        this.keyWrapper = keyWrapper;
        this.cipher = cipher;
        this.replayGuard = replayGuard;
        this.objectMapper = objectMapper;
        this.time = time;
    }

    /**
     * @throws ApiException SESSION_NOT_FOUND (unknown, revoked or foreign session),
     *     SESSION_EXPIRED, or DECRYPTION_FAILED (bad blob, wrong key, tampered, malformed JSON,
     *     stale ts, replayed nonce)
     */
    @Transactional(readOnly = true)
    public <T extends EncryptedPayload> T decrypt(
            String sessionId, String userId, String payloadBase64, Class<T> type) {
        Session session = activeSession(sessionId, userId);
        T payload = open(session, payloadBase64, type);
        if (payload.ts() == null
                || payload.nonce() == null
                || !NONCE.matcher(payload.nonce()).matches()) {
            throw new ApiException(ErrorCode.DECRYPTION_FAILED);
        }
        replayGuard.check(session.getId(), payload.nonce(), payload.ts());
        return payload;
    }

    private Session activeSession(String sessionId, String userId) {
        Session session =
                sessions.findById(sessionId)
                        .filter(candidate -> candidate.ownedBy(userId) && !candidate.isRevoked())
                        .orElseThrow(() -> new ApiException(ErrorCode.SESSION_NOT_FOUND));
        if (session.isExpired(time.now())) {
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        return session;
    }

    private <T> T open(Session session, String payloadBase64, Class<T> type) {
        byte[] key = keyWrapper.unwrap(session.getId(), session.getWrappedKey());
        byte[] plaintext = null;
        try {
            byte[] blob = Base64.getDecoder().decode(payloadBase64);
            plaintext = cipher.decrypt(key, blob, null);
            return objectMapper.readValue(plaintext, type);
        } catch (IllegalArgumentException | CryptoException | JacksonException e) {
            throw new ApiException(ErrorCode.DECRYPTION_FAILED);
        } finally {
            Arrays.fill(key, (byte) 0);
            if (plaintext != null) {
                Arrays.fill(plaintext, (byte) 0);
            }
        }
    }
}
