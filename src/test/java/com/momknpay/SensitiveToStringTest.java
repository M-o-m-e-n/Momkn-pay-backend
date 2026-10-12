package com.momknpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.momknpay.common.config.AppProperties;
import com.momknpay.common.crypto.SessionKeyAgreement;
import com.momknpay.payment.web.dto.ConfirmPayload;
import com.momknpay.payment.web.dto.ConfirmRequest;
import com.momknpay.payment.web.dto.InquiryPayload;
import com.momknpay.payment.web.dto.InquiryRequest;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Records print every component by default; the ones holding secrets must not, so an accidental
 * {@code log.info("{}", dto)} leaks nothing (CODING_STANDARDS §10.3).
 */
class SensitiveToStringTest {

    private static final String KEY = "q9Jx0l3pY8u2cVt1kQe7nR4sW6zB5mH0aD3fG8jK2Lc=";
    private static final String BLOB = "AAECAwQFBgcICQoLjXq9mWQ2Zx0fYk1c3gUqvLw7r0E=";

    @Test
    void derivedSessionKeyIsMasked() {
        byte[] key = Base64.getDecoder().decode(KEY);

        String text = new SessionKeyAgreement.Handshake("server-public-key", key).toString();

        assertThat(text).contains("server-public-key", "****").doesNotContain(KEY);
    }

    @Test
    void handshakePrivateKeyIsMasked() {
        String text = properties("private-key-value").toString();

        assertThat(text).doesNotContain("private-key-value", "master-key-value");
    }

    @Test
    void pinIsMasked() {
        assertThat(new ConfirmPayload("9876", "ab".repeat(16), 1L).toString())
                .doesNotContain("9876")
                .contains("****");
    }

    @Test
    void subscriberNumberIsMasked() {
        assertThat(new InquiryPayload("1024750891", "ab".repeat(16), 1L).toString())
                .doesNotContain("1024750891")
                .contains("******0891");
    }

    @Test
    void encryptedPayloadsAreMasked() {
        assertThat(new InquiryRequest("svc_elec_cairo", BLOB).toString()).doesNotContain(BLOB);
        assertThat(new ConfirmRequest("inq_1", BLOB).toString()).doesNotContain(BLOB);
    }

    private static AppProperties properties(String sessionPrivateKey) {
        Duration any = Duration.ofMinutes(1);
        return new AppProperties(
                "master-key-value",
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
