package com.momknpay.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.momknpay.common.config.AppProperties;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** U18: wrap/unwrap with the master key, bound to the session id (NFR-SEC-4). */
class KeyWrapperTest {

    private static final String MASTER_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private final SecureRandom random = new SecureRandom();
    private final KeyWrapper wrapper = new KeyWrapper(new AesGcmCipher(random), props(MASTER_KEY));

    @Test
    void wrapAndUnwrapReturnTheSessionKey() {
        byte[] sessionKey = new byte[32];
        random.nextBytes(sessionKey);

        byte[] wrapped = wrapper.wrap("ses_1", sessionKey);

        assertThat(wrapped).isNotEqualTo(sessionKey);
        assertThat(wrapper.unwrap("ses_1", wrapped)).isEqualTo(sessionKey);
    }

    @Test
    void wrappedKeyIsBoundToItsSession() {
        byte[] wrapped = wrapper.wrap("ses_1", new byte[32]);

        assertThatThrownBy(() -> wrapper.unwrap("ses_2", wrapped))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void invalidMasterKeyFailsFast() {
        assertThatThrownBy(() -> KeyWrapper.decodeMasterKey("not base64!"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                KeyWrapper.decodeMasterKey(
                                        Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> KeyWrapper.decodeMasterKey(""))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AppProperties props(String masterKey) {
        Duration any = Duration.ofMinutes(1);
        return new AppProperties(
                masterKey, any, any, any, any, any, any, new AppProperties.RateLimit(5));
    }
}
