package com.momknpay.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** U1–U3: AES-256-GCM round-trip, tamper detection, fresh IV (NFR-SEC-1). */
class AesGcmCipherTest {

    private final SecureRandom random = new SecureRandom();
    private final AesGcmCipher cipher = new AesGcmCipher(random);
    private final byte[] key = randomKey();

    @Test
    void roundTrip() {
        byte[] plaintext =
                "{\"pin\":\"1234\",\"nonce\":\"00\",\"ts\":1}".getBytes(StandardCharsets.UTF_8);

        byte[] blob = cipher.encrypt(key, plaintext, null);

        assertThat(blob)
                .hasSize(AesGcmCipher.IV_LENGTH + plaintext.length + AesGcmCipher.TAG_LENGTH);
        assertThat(cipher.decrypt(key, blob, null)).isEqualTo(plaintext);
    }

    @Test
    void tamperedCiphertextIsRejected() {
        byte[] blob = cipher.encrypt(key, "subscriber".getBytes(StandardCharsets.UTF_8), null);

        for (int position :
                new int[] {0, AesGcmCipher.IV_LENGTH, blob.length - 1}) { // iv, body, tag
            byte[] tampered = Arrays.copyOf(blob, blob.length);
            tampered[position] ^= 0x01;
            assertThatThrownBy(() -> cipher.decrypt(key, tampered, null))
                    .isInstanceOf(CryptoException.class);
        }
    }

    @Test
    void wrongKeyIsRejected() {
        byte[] blob = cipher.encrypt(key, "secret".getBytes(StandardCharsets.UTF_8), null);

        assertThatThrownBy(() -> cipher.decrypt(randomKey(), blob, null))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void mismatchedAadIsRejected() {
        byte[] blob = cipher.encrypt(key, "k".getBytes(StandardCharsets.UTF_8), bytes("ses_a"));

        assertThatThrownBy(() -> cipher.decrypt(key, blob, bytes("ses_b")))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void ivIsFreshPerMessage() {
        byte[] plaintext = "same plaintext every time".getBytes(StandardCharsets.UTF_8);
        Set<String> ivs = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            byte[] blob = cipher.encrypt(key, plaintext, null);
            ivs.add(Arrays.toString(Arrays.copyOf(blob, AesGcmCipher.IV_LENGTH)));
        }

        assertThat(ivs).hasSize(1000);
    }

    @Test
    void truncatedBlobAndBadKeyLengthAreRejected() {
        assertThatThrownBy(() -> cipher.decrypt(key, new byte[27], null))
                .isInstanceOf(CryptoException.class);
        assertThatThrownBy(() -> cipher.encrypt(new byte[16], new byte[1], null))
                .isInstanceOf(CryptoException.class);
    }

    private byte[] randomKey() {
        byte[] bytes = new byte[AesGcmCipher.KEY_LENGTH];
        random.nextBytes(bytes);
        return bytes;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
