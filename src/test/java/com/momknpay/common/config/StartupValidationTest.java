package com.momknpay.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.momknpay.common.crypto.PayloadKey;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The application refuses to start with a missing or malformed payload key (ADR-011). */
class StartupValidationTest {

    private static final String VALID_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(CoreConfig.class, PayloadKey.class)
                    .withPropertyValues(
                            "app.inquiry-ttl=PT5M",
                            "app.replay-window=PT120S",
                            "app.nonce-retention=PT5M",
                            "app.slow-delay=PT8S",
                            "app.pending-delay=PT10S",
                            "app.rate-limit.per-minute=5");

    @Test
    void startsWithAValidPayloadKey() {
        runner.withPropertyValues("app.payload-key=" + VALID_KEY)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(PayloadKey.class).bytes()).hasSize(32);
                        });
    }

    @Test
    void failsWithoutAPayloadKey() {
        runner.withPropertyValues("app.payload-key=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWithAPayloadKeyOfTheWrongLength() {
        runner.withPropertyValues("app.payload-key=AAECAwQFBgcICQoLDA0ODw==") // 16 bytes
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .rootCause()
                                        .hasMessageContaining("32 bytes"));
    }

    @Test
    void failsWithAPayloadKeyThatIsNotBase64() {
        runner.withPropertyValues("app.payload-key=not base64!")
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .rootCause()
                                        .hasMessageContaining("base64"));
    }

    @Test
    void theKeyHolderHandsOutCopies() {
        runner.withPropertyValues("app.payload-key=" + VALID_KEY)
                .run(
                        context -> {
                            PayloadKey key = context.getBean(PayloadKey.class);
                            key.bytes()[0] = 0x7F; // a caller zeroing its buffer must not hurt
                            assertThat(key.bytes()[0]).isZero();
                        });
    }
}
