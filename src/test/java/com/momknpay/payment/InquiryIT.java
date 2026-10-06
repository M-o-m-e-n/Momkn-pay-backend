package com.momknpay.payment;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.TestcontainersConfiguration;
import com.momknpay.common.web.Headers;
import com.momknpay.session.service.SessionService;
import com.momknpay.session.web.dto.CreateSessionResponse;
import com.momknpay.support.Payloads;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** POST /v1/payments/inquiry end to end with real encryption (FR-INQ, FR-MCK). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class InquiryIT {

    @Autowired private MockMvc mvc;
    @Autowired private SessionService sessionService;
    @Autowired private JdbcTemplate jdbc;

    private CreateSessionResponse session;

    @BeforeEach
    void openSession() {
        session = sessionService.create("usr_01");
    }

    @Test
    void workedExampleReturnsTheContractQuote(CapturedOutput output) throws Exception {
        String body =
                inquire("svc_elec_cairo", Payloads.inquiry(session.sessionKey(), "1024750891"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.serviceId").value("svc_elec_cairo"))
                        .andExpect(jsonPath("$.customerName").value("Mina A."))
                        .andExpect(jsonPath("$.amountDue").value(24750))
                        .andExpect(jsonPath("$.serviceFee").value(500))
                        .andExpect(jsonPath("$.vat").value(70))
                        .andExpect(jsonPath("$.total").value(25320))
                        .andExpect(jsonPath("$.currency").value("EGP"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String inquiryId = JsonPath.read(body, "$.inquiryId");
        Instant expiresAt = Instant.parse(JsonPath.read(body, "$.expiresAt"));
        assertThat(inquiryId).matches("^inq_[0-9a-f]{16}$");
        assertThat(Duration.between(Instant.now(), expiresAt))
                .isBetween(Duration.ofMinutes(4), Duration.ofMinutes(5));

        Map<String, Object> row =
                jdbc.queryForMap("SELECT * FROM inquiries WHERE id = ?", inquiryId);
        assertThat(row)
                .containsEntry("user_id", "usr_01")
                .containsEntry("session_id", session.sessionId())
                .containsEntry("rule", "NORMAL")
                .containsEntry("status", "OPEN")
                .containsEntry("total", 25320L);
        assertThat(((Timestamp) row.get("expires_at")).toInstant()).isEqualTo(expiresAt);

        assertThat(output.getAll()).contains("******0891").doesNotContain("1024750891");
    }

    @Test
    void lastDigitRulesReachTheClient() throws Exception {
        inquire("svc_elec_cairo", Payloads.inquiry(session.sessionKey(), "1024750890"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SUBSCRIBER_NOT_FOUND"));
        inquire("svc_elec_cairo", Payloads.inquiry(session.sessionKey(), "1024750899"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BILL_ALREADY_PAID"));
        inquire("svc_elec_cairo", Payloads.inquiry(session.sessionKey(), "1024750896"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountDue").value(510000)); // above maxAmount 500000
        for (String subscriber : new String[] {"1024750897", "1024750898"}) {
            inquire("svc_elec_cairo", Payloads.inquiry(session.sessionKey(), subscriber))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(25320));
        }
    }

    @Test
    void subscriberNumberMustMatchTheServicePattern() throws Exception {
        inquire("svc_elec_cairo", Payloads.inquiry(session.sessionKey(), "102475089")) // 9 digits
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.field").value("subscriberNumber"));
    }

    @Test
    void unknownDeletedAndInactiveServices() throws Exception {
        String payload = Payloads.inquiry(session.sessionKey(), "1024750891");
        inquire("svc_nope", payload)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SERVICE_NOT_FOUND"));
        inquire("svc_water_legacy", payload)
                .andExpect(jsonPath("$.error.code").value("SERVICE_NOT_FOUND"));
        inquire("svc_elec_alex", payload)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    void replayedPayloadIsRejected() throws Exception {
        String payload = Payloads.inquiry(session.sessionKey(), "1024750891");
        inquire("svc_elec_cairo", payload).andExpect(status().isOk());

        inquire("svc_elec_cairo", payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DECRYPTION_FAILED"));
    }

    @Test
    void sessionHeaderIsRequiredAndMustBeTheUsersOwn() throws Exception {
        String payload = Payloads.inquiry(session.sessionKey(), "1024750891");
        mvc.perform(
                        withClientHeaders(post("/v1/payments/inquiry"))
                                .header(Headers.USER_ID, "usr_01")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("svc_elec_cairo", payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.field").value("X-Session-Id"));

        mvc.perform(
                        withClientHeaders(post("/v1/payments/inquiry"))
                                .header(Headers.USER_ID, "usr_02")
                                .header(Headers.SESSION_ID, session.sessionId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("svc_elec_cairo", payload)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_FOUND"));
    }

    @Test
    void slowServiceAnswersLate() throws Exception {
        long start = System.nanoTime();

        inquire("svc_elec_canal_slow", Payloads.inquiry(session.sessionKey(), "1024750891"))
                .andExpect(status().isOk());

        assertThat(Duration.ofNanos(System.nanoTime() - start))
                .isGreaterThanOrEqualTo(Duration.ofMillis(200)); // test delay, 8 s in production
    }

    private ResultActions inquire(String serviceId, String payload) throws Exception {
        return mvc.perform(
                withClientHeaders(post("/v1/payments/inquiry"))
                        .header(Headers.USER_ID, "usr_01")
                        .header(Headers.SESSION_ID, session.sessionId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(serviceId, payload)));
    }

    private static String body(String serviceId, String payload) {
        return """
        {"serviceId":"%s","payload":"%s"}\
        """
                .formatted(serviceId, payload);
    }
}
