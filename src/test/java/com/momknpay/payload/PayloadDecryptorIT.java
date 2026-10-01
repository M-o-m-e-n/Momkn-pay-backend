package com.momknpay.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.momknpay.TestcontainersConfiguration;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.payload.service.EncryptedPayload;
import com.momknpay.payload.service.PayloadDecryptor;
import com.momknpay.support.TestCrypto;
import java.util.Arrays;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;

/** Decrypting client payloads with the static shared key, against PostgreSQL (NFR-SEC-1…3). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class PayloadDecryptorIT {

    private static final String KEY = TestCrypto.TEST_PAYLOAD_KEY;

    /** A valid AES-256 key that is not the configured one: 32 bytes of 0xFF. */
    private static final String OTHER_KEY = otherKey();

    /** A payload shaped like the inquiry plaintext. */
    record ProbePayload(String subscriberNumber, String nonce, Long ts)
            implements EncryptedPayload {}

    @Autowired private PayloadDecryptor decryptor;
    @Autowired private TransactionTemplate transactions;

    @Test
    void clientEncryptedPayloadRoundTrips(CapturedOutput output) {
        String payload = encrypt(KEY, "1024750891", TestCrypto.nonce(), TestCrypto.nowTs());

        ProbePayload plain = decrypt(payload);

        assertThat(plain.subscriberNumber()).isEqualTo("1024750891");
        assertThat(output.getAll()).doesNotContain("1024750891").doesNotContain(payload);
    }

    @Test
    void replayedPayloadIsRejected() {
        String payload = encrypt(KEY, "1024750891", TestCrypto.nonce(), TestCrypto.nowTs());
        decrypt(payload);

        assertDecryptionFailed(() -> decrypt(payload));
    }

    @Test
    void sameNonceInANewPayloadIsRejectedToo() {
        String nonce = TestCrypto.nonce();
        decrypt(encrypt(KEY, "1024750891", nonce, TestCrypto.nowTs()));

        assertDecryptionFailed(
                () -> decrypt(encrypt(KEY, "1024750892", nonce, TestCrypto.nowTs())));
    }

    @Test
    void staleTimestampIsRejected() {
        String payload = encrypt(KEY, "1024750891", TestCrypto.nonce(), TestCrypto.nowTs() - 121);

        assertDecryptionFailed(() -> decrypt(payload));
    }

    @Test
    void badBlobsAreRejected() {
        String valid = encrypt(KEY, "1024750891", TestCrypto.nonce(), TestCrypto.nowTs());
        byte[] tampered = Base64.getDecoder().decode(valid);
        tampered[20] ^= 0x01;

        assertDecryptionFailed(() -> decrypt("%%% not base64 %%%"));
        assertDecryptionFailed(() -> decrypt(Base64.getEncoder().encodeToString(tampered)));
        assertDecryptionFailed(() -> decrypt("AAAA"));
    }

    @Test
    void payloadEncryptedWithAnotherKeyIsRejected() {
        String payload = encrypt(OTHER_KEY, "1024750891", TestCrypto.nonce(), TestCrypto.nowTs());

        assertDecryptionFailed(() -> decrypt(payload));
    }

    @Test
    void malformedPlaintextIsRejected() {
        String notJson = TestCrypto.encrypt(KEY, "not json");
        String badNonce = encrypt(KEY, "1024750891", "NOT-HEX", TestCrypto.nowTs());
        String noTs =
                TestCrypto.encrypt(
                        KEY,
                        "{\"subscriberNumber\":\"1\",\"nonce\":\"" + TestCrypto.nonce() + "\"}");

        assertDecryptionFailed(() -> decrypt(notJson));
        assertDecryptionFailed(() -> decrypt(badNonce));
        assertDecryptionFailed(() -> decrypt(noTs));
    }

    @Test
    void nonceStaysConsumedWhenTheCallerRollsBack() {
        String payload = encrypt(KEY, "1024750891", TestCrypto.nonce(), TestCrypto.nowTs());

        assertThatThrownBy(
                        () ->
                                transactions.executeWithoutResult(
                                        status -> {
                                            decrypt(payload);
                                            throw new IllegalStateException("business failure");
                                        }))
                .isInstanceOf(IllegalStateException.class);

        assertDecryptionFailed(() -> decrypt(payload));
    }

    private ProbePayload decrypt(String payload) {
        return decryptor.decrypt(payload, ProbePayload.class);
    }

    private static String encrypt(String key, String subscriber, String nonce, long ts) {
        return TestCrypto.encrypt(
                key,
                """
                {"subscriberNumber":"%s","nonce":"%s","ts":%d}\
                """
                        .formatted(subscriber, nonce, ts));
    }

    private static String otherKey() {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) 0xFF);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static void assertDecryptionFailed(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.DECRYPTION_FAILED);
    }
}
