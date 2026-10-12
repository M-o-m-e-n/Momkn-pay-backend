package com.momknpay.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.momknpay.common.config.AppProperties;
import com.momknpay.support.TestCrypto;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ServerKeyPairTest {

    private final SessionKeyAgreement agreement = new SessionKeyAgreement(new SecureRandom());

    @Test
    void publicKeyAndKeyIdComeFromThePrivateKey() {
        KeyPair pair = TestCrypto.clientKeyPair();

        ServerKeyPair serverKeyPair = new ServerKeyPair(properties(pkcs8(pair)), agreement);

        assertThat(serverKeyPair.publicKeyBase64()).isEqualTo(TestCrypto.spki(pair.getPublic()));
        assertThat(serverKeyPair.keyId()).matches("[0-9a-f]{16}");
        assertThat(serverKeyPair.privateKey().getS())
                .isEqualTo(((java.security.interfaces.ECPrivateKey) pair.getPrivate()).getS());
    }

    @Test
    void toStringShowsOnlyTheKeyId() {
        KeyPair pair = TestCrypto.clientKeyPair();
        String privateKey = pkcs8(pair);

        ServerKeyPair serverKeyPair = new ServerKeyPair(properties(privateKey), agreement);

        assertThat(serverKeyPair.toString())
                .contains(serverKeyPair.keyId())
                .doesNotContain(privateKey);
    }

    @Test
    void rejectsValuesThatAreNotAPkcs8EcKey() {
        assertThatThrownBy(() -> ServerKeyPair.decodePrivateKey("not base64 !"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_SESSION_PRIVATE_KEY");
        assertThatThrownBy(() -> ServerKeyPair.decodePrivateKey(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ServerKeyPair.decodePrivateKey("AAECAwQFBgcICQoLDA0ODw=="))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PKCS#8");
    }

    @Test
    void rejectsAKeyOnAnotherCurve() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp384r1"));
        String p384 = pkcs8(generator.generateKeyPair());

        assertThatThrownBy(() -> new ServerKeyPair(properties(p384), agreement))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("P-256");
    }

    private static String pkcs8(KeyPair pair) {
        return Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
    }

    private static AppProperties properties(String sessionPrivateKey) {
        Duration any = Duration.ofMinutes(1);
        return new AppProperties(
                "unused",
                sessionPrivateKey,
                any,
                any,
                any,
                any,
                any,
                any,
                new AppProperties.RateLimit(5));
    }
}
