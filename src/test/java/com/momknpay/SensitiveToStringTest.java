package com.momknpay;

import static org.assertj.core.api.Assertions.assertThat;

import com.momknpay.payment.web.dto.ConfirmPayload;
import com.momknpay.payment.web.dto.ConfirmRequest;
import com.momknpay.payment.web.dto.InquiryPayload;
import com.momknpay.payment.web.dto.InquiryRequest;
import com.momknpay.session.web.dto.CreateSessionResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Records print every component by default; the ones holding secrets must not, so an accidental
 * {@code log.info("{}", dto)} leaks nothing (CODING_STANDARDS §10.3).
 */
class SensitiveToStringTest {

    private static final String KEY = "q9Jx0l3pY8u2cVt1kQe7nR4sW6zB5mH0aD3fG8jK2Lc=";
    private static final String BLOB = "AAECAwQFBgcICQoLjXq9mWQ2Zx0fYk1c3gUqvLw7r0E=";

    @Test
    void sessionKeyIsMasked() {
        String text = new CreateSessionResponse("ses_1", KEY, Instant.EPOCH).toString();

        assertThat(text).contains("ses_1").doesNotContain(KEY);
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
