package com.momknpay.support;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts payloads exactly as the mobile clients do, written independently of the server's {@code
 * AesGcmCipher} so tests catch a server/client mismatch.
 */
public final class TestCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestCrypto() {}

    /** A fresh nonce: 16 random bytes as 32 lowercase hex characters. */
    public static String nonce() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    public static long nowTs() {
        return Instant.now().getEpochSecond();
    }

    /** base64( iv[12] ‖ AES-256-GCM(json) ‖ tag[16] ), fresh IV, no AAD. */
    public static String encrypt(String sessionKeyBase64, String json) {
        try {
            byte[] key = Base64.getDecoder().decode(sessionKeyBase64);
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, iv));
            byte[] sealed = cipher.doFinal(json.getBytes(StandardCharsets.UTF_8));
            byte[] blob = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, blob, 0, iv.length);
            System.arraycopy(sealed, 0, blob, iv.length, sealed.length);
            return Base64.getEncoder().encodeToString(blob);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
