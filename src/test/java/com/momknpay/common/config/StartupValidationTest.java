package com.momknpay.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.momknpay.common.crypto.AesGcmCipher;
import com.momknpay.common.crypto.KeyWrapper;
import com.momknpay.common.crypto.ServerKeyPair;
import com.momknpay.common.crypto.SessionKeyAgreement;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The application refuses to start with a missing or malformed master key or handshake key
 * (NFR-SEC-4, §13).
 */
class StartupValidationTest {

    private static final String VALID_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String VALID_PRIVATE_KEY =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg8qgFDYscBWgzcBO8y/v2040h1EuiCfKNfx9+H9sVliehRANCAAQUZg9XUKrEQpgXVBnUELadykRAZEMOMPFciGllpttAd70nMe/sI97TI7Afw+kSSFJS7YXCVF2/2sPCtOgS/E+K";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            CoreConfig.class,
                            AesGcmCipher.class,
                            KeyWrapper.class,
                            SessionKeyAgreement.class,
                            ServerKeyPair.class)
                    .withPropertyValues(
                            "app.session-ttl=PT30M",
                            "app.inquiry-ttl=PT5M",
                            "app.replay-window=PT120S",
                            "app.nonce-retention=PT5M",
                            "app.slow-delay=PT8S",
                            "app.pending-delay=PT10S",
                            "app.rate-limit.per-minute=5");

    @Test
    void startsWithAValidMasterKey() {
        valid().run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .hasSingleBean(KeyWrapper.class)
                                        .hasSingleBean(ServerKeyPair.class));
    }

    @Test
    void failsWithoutAMasterKey() {
        valid().withPropertyValues("app.master-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWithAMasterKeyOfTheWrongLength() {
        valid().withPropertyValues("app.master-key=AAECAwQFBgcICQoLDA0ODw==") // 16 bytes
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .rootCause()
                                        .hasMessageContaining("32 bytes"));
    }

    @Test
    void failsWithoutAHandshakeKey() {
        valid().withPropertyValues("app.session-private-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWithAHandshakeKeyThatIsNotAnEcPrivateKey() {
        valid().withPropertyValues("app.session-private-key=" + VALID_KEY) // 32 random-like bytes
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .rootCause()
                                        .hasMessageContaining("APP_SESSION_PRIVATE_KEY"));
    }

    private ApplicationContextRunner valid() {
        return runner.withPropertyValues(
                "app.master-key=" + VALID_KEY, "app.session-private-key=" + VALID_PRIVATE_KEY);
    }
}
