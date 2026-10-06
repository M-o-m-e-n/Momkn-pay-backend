package com.momknpay.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.momknpay.common.crypto.AesGcmCipher;
import com.momknpay.common.crypto.KeyWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The application refuses to start with a missing or malformed master key (NFR-SEC-4, §13). */
class StartupValidationTest {

    private static final String VALID_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(CoreConfig.class, AesGcmCipher.class, KeyWrapper.class)
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
        runner.withPropertyValues("app.master-key=" + VALID_KEY)
                .run(context -> assertThat(context).hasNotFailed().hasSingleBean(KeyWrapper.class));
    }

    @Test
    void failsWithoutAMasterKey() {
        runner.withPropertyValues("app.master-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWithAMasterKeyOfTheWrongLength() {
        runner.withPropertyValues("app.master-key=AAECAwQFBgcICQoLDA0ODw==") // 16 bytes
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .rootCause()
                                        .hasMessageContaining("32 bytes"));
    }
}
