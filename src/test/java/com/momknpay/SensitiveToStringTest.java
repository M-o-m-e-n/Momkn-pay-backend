package com.momknpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.momknpay.common.config.AppProperties;
import com.momknpay.payment.web.dto.ConfirmPayload;
import com.momknpay.payment.web.dto.ConfirmRequest;
import com.momknpay.payment.web.dto.InquiryPayload;
import com.momknpay.payment.web.dto.InquiryRequest;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Records print every component by default; the ones holding secrets must not, so an accidental
 * {@code log.info("{}", dto)} leaks nothing (CODING_STANDARDS §10.3).
 */
class SensitiveToStringTest {

    private static final String BLOB = "AAECAwQFBgcICQoLjXq9mWQ2Zx0fYk1c3gUqvLw7r0E=";

    @Test
    void payloadKeyIsMaskedInTheProperties() {
        Duration any = Duration.ofMinutes(1);
        String key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

        String text =
                new AppProperties(key, any, any, any, any, any, new AppProperties.RateLimit(5))
                        .toString();

        assertThat(text).doesNotContain(key).contains("****");
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
}
