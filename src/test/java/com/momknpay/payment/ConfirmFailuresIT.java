package com.momknpay.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.momknpay.TestcontainersConfiguration;
import com.momknpay.support.Payloads;
import com.momknpay.support.PaymentClient;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Every failure of POST /v1/payments/confirm on the real stack (FR-PAY-5…8, SRS §6.1). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConfirmFailuresIT {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void wrongPinsCountAndTheThirdInvalidatesTheInquiry() throws Exception {
        PaymentClient omar = client("usr_03"); // PIN 9999
        String inquiryId = omar.openInquiry("svc_elec_cairo", "1024750891");

        for (int attempt = 1; attempt <= 3; attempt++) {
            omar.confirm(inquiryId, UUID.randomUUID(), "1234")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.error.field").value("pin"));
        }
        assertThat(inquiryColumn(inquiryId, "status")).isEqualTo("INVALIDATED");

        omar.confirm(inquiryId, UUID.randomUUID(), "9999") // even the right PIN
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("INQUIRY_INVALIDATED"));
        assertThat(transactionsFor(inquiryId)).isEmpty();
    }

    @Test
    void theRightPinStillWorksAfterOneMistake() throws Exception {
        PaymentClient omar = client("usr_03");
        String inquiryId = omar.openInquiry("svc_elec_cairo", "1024750891");

        omar.confirm(inquiryId, UUID.randomUUID(), "0000").andExpect(status().isBadRequest());
        omar.confirm(inquiryId, UUID.randomUUID(), "9999")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void expiredInquiryIsGone() throws Exception {
        PaymentClient mina = client("usr_01");
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750891");
        jdbc.update(
                "UPDATE inquiries SET expires_at = now() - interval '1 hour' WHERE id = ?",
                inquiryId);

        mina.confirm(inquiryId, UUID.randomUUID(), "1234")
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("INQUIRY_EXPIRED"));
    }

    @Test
    void largeBillIsOutOfRangeAndCreatesNothing() throws Exception {
        PaymentClient mina = client("usr_01");
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750896");

        mina.confirm(inquiryId, UUID.randomUUID(), "1234")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("AMOUNT_OUT_OF_RANGE"));
        assertThat(transactionsFor(inquiryId)).isEmpty();
    }

    @Test
    void declineIsRecordedReplayedAndRetryable() throws Exception {
        PaymentClient mina = client("usr_01");
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750897");
        UUID key = UUID.randomUUID();
        String payload = Payloads.confirm("1234");

        mina.confirmWithPayload(inquiryId, key, payload)
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_BALANCE"));
        mina.confirmWithPayload(inquiryId, key, payload) // same key → same answer
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_BALANCE"));
        assertThat(transactionsFor(inquiryId)).containsExactly("FAILED");
        assertThat(inquiryColumn(inquiryId, "status")).isEqualTo("OPEN");

        mina.confirm(inquiryId, UUID.randomUUID(), "1234") // "top up and retry": new key
                .andExpect(status().isPaymentRequired());
        assertThat(transactionsFor(inquiryId)).containsExactly("FAILED", "FAILED");
    }

    @Test
    void serviceThatWentDownAfterTheInquiryIsUnavailable() throws Exception {
        PaymentClient mina = client("usr_01");
        String inquiryId = mina.openInquiry("svc_water_giza", "101426001");
        jdbc.update("UPDATE services SET is_active = FALSE WHERE id = 'svc_water_giza'");
        try {
            mina.confirm(inquiryId, UUID.randomUUID(), "1234")
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
        } finally {
            jdbc.update("UPDATE services SET is_active = TRUE WHERE id = 'svc_water_giza'");
        }
    }

    @Test
    void malformedPinOrPayloadIsRejected() throws Exception {
        PaymentClient mina = client("usr_01");
        String inquiryId = mina.openInquiry("svc_elec_cairo", "1024750891");

        mina.confirm(inquiryId, UUID.randomUUID(), "12a4")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("pin"));
        mina.confirmWithPayload(inquiryId, UUID.randomUUID(), "bm90IGEgcmVhbCBwYXlsb2Fk")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DECRYPTION_FAILED"));
        assertThat(inquiryColumn(inquiryId, "failed_pin_attempts")).isEqualTo("0");
    }

    private PaymentClient client(String userId) {
        return new PaymentClient(mvc, userId);
    }

    private String inquiryColumn(String inquiryId, String column) {
        return jdbc.queryForObject(
                "SELECT " + column + "::text FROM inquiries WHERE id = ?", String.class, inquiryId);
    }

    private List<String> transactionsFor(String inquiryId) {
        return jdbc.queryForList(
                "SELECT status FROM transactions WHERE inquiry_id = ? ORDER BY seq",
                String.class,
                inquiryId);
    }
}
