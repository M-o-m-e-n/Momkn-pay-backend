package com.momknpay.common.crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM, the only class allowed to touch {@link Cipher} (CODING_STANDARDS §10.2).
 *
 * <p>Wire format: {@code iv(12) ‖ ciphertext ‖ tag(16)}. A fresh random IV is generated for every
 * encryption — reusing an IV with the same key breaks GCM completely.
 */
@Component
public class AesGcmCipher {

    public static final int KEY_LENGTH = 32;
    public static final int IV_LENGTH = 12;
    public static final int TAG_LENGTH = 16;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int TAG_BITS = TAG_LENGTH * 8;

    private final SecureRandom random;

    public AesGcmCipher(SecureRandom random) {
        this.random = random;
    }

    /**
     * @param aad additional authenticated data, or null (client payloads use none)
     */
    public byte[] encrypt(byte[] key, byte[] plaintext, byte[] aad) {
        requireKey(key);
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            if (aad != null) {
                cipher.updateAAD(aad);
            }
            byte[] sealed = cipher.doFinal(plaintext);
            byte[] blob = new byte[IV_LENGTH + sealed.length];
            System.arraycopy(iv, 0, blob, 0, IV_LENGTH);
            System.arraycopy(sealed, 0, blob, IV_LENGTH, sealed.length);
            return blob;
        } catch (GeneralSecurityException e) {
            throw new CryptoException("encryption failed", e);
        }
    }

    /**
     * @throws CryptoException on a wrong key, wrong AAD, tampered or truncated blob
     */
    public byte[] decrypt(byte[] key, byte[] blob, byte[] aad) {
        requireKey(key);
        if (blob == null || blob.length < IV_LENGTH + TAG_LENGTH) {
            throw new CryptoException("blob too short");
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(TAG_BITS, blob, 0, IV_LENGTH));
            if (aad != null) {
                cipher.updateAAD(aad);
            }
            return cipher.doFinal(blob, IV_LENGTH, blob.length - IV_LENGTH);
        } catch (AEADBadTagException e) {
            throw new CryptoException("authentication tag mismatch", e);
        } catch (GeneralSecurityException e) {
            throw new CryptoException("decryption failed", e);
        }
    }

    private static void requireKey(byte[] key) {
        if (key == null || key.length != KEY_LENGTH) {
            throw new CryptoException("key must be 32 bytes");
        }
    }
}
