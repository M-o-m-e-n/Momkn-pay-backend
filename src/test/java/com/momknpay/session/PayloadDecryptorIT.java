package com.momknpay.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.momknpay.TestcontainersConfiguration;
import com.momknpay.common.error.ApiException;
import com.momknpay.common.error.ErrorCode;
import com.momknpay.session.service.EncryptedPayload;
import com.momknpay.session.service.PayloadDecryptor;
import com.momknpay.session.service.SessionService;
import com.momknpay.session.web.dto.CreateSessionResponse;
import com.momknpay.support.TestCrypto;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Decrypting client payloads end to end against PostgreSQL (FR-SES-2/4, NFR-SEC-1…3). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class PayloadDecryptorIT {

    /** A payload shaped like the inquiry plaintext. */
    record ProbePayload(String subscriberNumber, String nonce, Long ts)
            implements EncryptedPayload {}

    @Autowired private SessionService sessionService;
    @Autowired private PayloadDecryptor decryptor;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;

    private CreateSessionResponse session;

    @BeforeEach
    void createSession() {
        session = sessionService.create("usr_01");
    }

    @Test
    void clientEncryptedPayloadRoundTrips(CapturedOutput output) {
        String payload =
                encrypt(session.sessionKey(), "1024750891", TestCrypto.nonce(), TestCrypto.nowTs());

        ProbePayload plain = decrypt(payload);

        assertThat(plain.subscriberNumber()).isEqualTo("1024750891");
        assertThat(output.getAll()).doesNotContain("1024750891").doesNotContain(payload);
    }

    @Test
    void replayedPayloadIsRejected() {
        String payload =
                encrypt(session.sessionKey(), "1024750891", TestCrypto.nonce(), TestCrypto.nowTs());
        decrypt(payload);

        assertFails(() -> decrypt(payload), ErrorCode.DECRYPTION_FAILED);
    }

    @Test
    void staleTimestampIsRejected() {
        String payload =
                encrypt(
                        session.sessionKey(),
                        "1024750891",
                        TestCrypto.nonce(),
                        TestCrypto.nowTs() - 121);

        assertFails(() -> decrypt(payload), ErrorCode.DECRYPTION_FAILED);
    }

    @Test
    void badBlobsAreRejected() {
        String valid =
                encrypt(session.sessionKey(), "1024750891", TestCrypto.nonce(), TestCrypto.nowTs());
        byte[] tampered = Base64.getDecoder().decode(valid);
        tampered[20] ^= 0x01;

        assertFails(() -> decrypt("%%% not base64 %%%"), ErrorCode.DECRYPTION_FAILED);
        assertFails(
                () -> decrypt(Base64.getEncoder().encodeToString(tampered)),
                ErrorCode.DECRYPTION_FAILED);
        assertFails(() -> decrypt("AAAA"), ErrorCode.DECRYPTION_FAILED);
    }

    @Test
    void payloadEncryptedWithAnotherSessionsKeyIsRejected() {
        CreateSessionResponse other = sessionService.create("usr_01");
        String payload =
                encrypt(other.sessionKey(), "1024750891", TestCrypto.nonce(), TestCrypto.nowTs());

        assertFails(() -> decrypt(payload), ErrorCode.DECRYPTION_FAILED);
    }

    @Test
    void malformedPlaintextIsRejected() {
        String notJson = TestCrypto.encrypt(session.sessionKey(), "not json");
        String badNonce =
                encrypt(session.sessionKey(), "1024750891", "NOT-HEX", TestCrypto.nowTs());
        String noTs =
                TestCrypto.encrypt(
                        session.sessionKey(),
                        "{\"subscriberNumber\":\"1\",\"nonce\":\"" + TestCrypto.nonce() + "\"}");

        assertFails(() -> decrypt(notJson), ErrorCode.DECRYPTION_FAILED);
        assertFails(() -> decrypt(badNonce), ErrorCode.DECRYPTION_FAILED);
        assertFails(() -> decrypt(noTs), ErrorCode.DECRYPTION_FAILED);
    }

    @Test
    void expiredSessionIsSessionExpired() {
        jdbc.update(
                "UPDATE sessions SET expires_at = now() - interval '1 second' WHERE id = ?",
                session.sessionId());
        String payload =
                encrypt(session.sessionKey(), "1024750891", TestCrypto.nonce(), TestCrypto.nowTs());

        assertFails(() -> decrypt(payload), ErrorCode.SESSION_EXPIRED);
    }

    @Test
    void revokedOrForeignSessionIsNotFound() {
        String payload =
                encrypt(session.sessionKey(), "1024750891", TestCrypto.nonce(), TestCrypto.nowTs());

        assertFails(
                () -> decryptor.decrypt(session.sessionId(), "usr_02", payload, ProbePayload.class),
                ErrorCode.SESSION_NOT_FOUND);

        sessionService.revoke("usr_01", session.sessionId());
        assertFails(() -> decrypt(payload), ErrorCode.SESSION_NOT_FOUND);
    }

    @Test
    void nonceStaysConsumedWhenTheCallerRollsBack() {
        String payload =
                encrypt(session.sessionKey(), "1024750891", TestCrypto.nonce(), TestCrypto.nowTs());

        assertThatThrownBy(
                        () ->
                                transactions.executeWithoutResult(
                                        status -> {
                                            decrypt(payload);
                                            throw new IllegalStateException("business failure");
                                        }))
                .isInstanceOf(IllegalStateException.class);

        assertFails(() -> decrypt(payload), ErrorCode.DECRYPTION_FAILED);
    }

    private ProbePayload decrypt(String payload) {
        return decryptor.decrypt(session.sessionId(), "usr_01", payload, ProbePayload.class);
    }

    private static String encrypt(String key, String subscriber, String nonce, long ts) {
        return TestCrypto.encrypt(
                key,
                """
                {"subscriberNumber":"%s","nonce":"%s","ts":%d}\
                """
                        .formatted(subscriber, nonce, ts));
    }

    private static void assertFails(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(expected);
    }
}
