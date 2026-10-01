package com.momknpay.payload.service;

import com.momknpay.common.crypto.AesGcmCipher;
import com.momknpay.common.crypto.CryptoException;
import com.momknpay.common.crypto.PayloadKey;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns a {@code payload} string into its plaintext object (LLD §8): AES-GCM decryption with the
 * static shared key, JSON parsing, then the replay guard. Nothing it handles is ever logged.
 */
@Component
public class PayloadDecryptor {

    private static final Pattern NONCE = Pattern.compile("^[0-9a-f]{32}$");

    private final PayloadKey payloadKey;
    private final AesGcmCipher cipher;
    private final ReplayGuard replayGuard;
    private final ObjectMapper objectMapper;

    public PayloadDecryptor(
            PayloadKey payloadKey,
            AesGcmCipher cipher,
            ReplayGuard replayGuard,
            ObjectMapper objectMapper) {
        this.payloadKey = payloadKey;
        this.cipher = cipher;
        this.replayGuard = replayGuard;
        this.objectMapper = objectMapper;
    }

    /**
     * @throws ApiException DECRYPTION_FAILED for a bad blob, wrong key, tampered bytes, malformed
     *     JSON, a stale ts or a replayed nonce
     */
    public <T extends EncryptedPayload> T decrypt(String payloadBase64, Class<T> type) {
        T payload = open(payloadBase64, type);
        if (payload.ts() == null
                || payload.nonce() == null
                || !NONCE.matcher(payload.nonce()).matches()) {
            throw new ApiException(ErrorCode.DECRYPTION_FAILED);
        }
        replayGuard.check(payload.nonce(), payload.ts());
        return payload;
    }

    private <T> T open(String payloadBase64, Class<T> type) {
        byte[] key = payloadKey.bytes();
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
