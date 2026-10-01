package com.momknpay.common.crypto;

import com.momknpay.common.config.AppProperties;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * The static AES-256 key that encrypts request payloads (ADR-011). One key for every client: it is
 * configured through {@code APP_PAYLOAD_KEY} and built into the apps, so it protects payloads from
 * intermediaries and logs, not from anyone who has an app build.
 *
 * <p>Startup fails unless the key is valid base64 of exactly 32 bytes.
 */
@Component
public class PayloadKey {

    private final byte[] key;

    public PayloadKey(AppProperties properties) {
        this.key = decode(properties.payloadKey());
    }

    /** A copy, so callers can zero their buffer without destroying the configured key. */
    public byte[] bytes() {
        return key.clone();
    }

    static byte[] decode(String base64) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64 == null ? "" : base64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("APP_PAYLOAD_KEY is not valid base64");
        }
        if (decoded.length != AesGcmCipher.KEY_LENGTH) {
            throw new IllegalStateException(
                    "APP_PAYLOAD_KEY must decode to 32 bytes (openssl rand -base64 32)");
        }
        return decoded;
    }
}
