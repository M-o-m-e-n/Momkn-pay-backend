package com.momknpay.common.crypto;

import com.momknpay.common.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Seals session keys at rest with the master key (NFR-SEC-4). The session id is bound as AAD, so a
 * wrapped key copied onto another session row does not unwrap.
 */
@Component
public class KeyWrapper {

    private final AesGcmCipher cipher;
    private final byte[] masterKey;

    public KeyWrapper(AesGcmCipher cipher, AppProperties properties) {
        this.cipher = cipher;
        this.masterKey = decodeMasterKey(properties.masterKey());
    }

    public byte[] wrap(String sessionId, byte[] sessionKey) {
        return cipher.encrypt(masterKey, sessionKey, aad(sessionId));
    }

    public byte[] unwrap(String sessionId, byte[] wrappedKey) {
        return cipher.decrypt(masterKey, wrappedKey, aad(sessionId));
    }

    private static byte[] aad(String sessionId) {
        return sessionId.getBytes(StandardCharsets.UTF_8);
    }

    /** Fails startup unless the master key is valid base64 of exactly 32 bytes. */
    static byte[] decodeMasterKey(String base64) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_MASTER_KEY is not valid base64");
        }
        if (key.length != AesGcmCipher.KEY_LENGTH) {
            throw new IllegalStateException(
                    "APP_MASTER_KEY must decode to 32 bytes (openssl rand -base64 32)");
        }
        return key;
    }
}
