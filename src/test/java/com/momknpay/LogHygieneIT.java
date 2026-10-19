package com.momknpay;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.common.web.Headers;
import com.momknpay.session.web.dto.CreateSessionResponse;
import com.momknpay.support.Payloads;
import com.momknpay.support.PaymentClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * I10: runs every sensitive path — session, inquiry, confirm (right and wrong CVV), replay,
 * malformed input, receipt — and asserts that no secret reaches the logs (NFR-SEC-6,
 * CODING_STANDARDS §10.3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class LogHygieneIT {

    private static final String SUBSCRIBER = "1024750891";
    private static final Pattern CVV_IN_LOG = Pattern.compile("(?i)\"?cvv\"?\\s*[:=]\\s*\"?\\d{3}");

    @Autowired private MockMvc mvc;

    @Test
    void noSecretEverReachesTheLogs(CapturedOutput output) throws Exception {
        List<String> secrets = new ArrayList<>();

        // session over HTTP: the key is in the response body only
        String sessionBody =
                mvc.perform(
                                withClientHeaders(post("/v1/sessions"))
                                        .header(Headers.USER_ID, "usr_03"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        CreateSessionResponse session =
                new CreateSessionResponse(
                        JsonPath.read(sessionBody, "$.sessionId"),
                        JsonPath.read(sessionBody, "$.sessionKey"),
                        Instant.parse(JsonPath.read(sessionBody, "$.expiresAt")));
        secrets.add(session.sessionKey());
        PaymentClient omar = new PaymentClient(mvc, "usr_03", session);

        // inquiry: the subscriber number travels encrypted and is logged masked only
        String inquiryPayload = Payloads.inquiry(session.sessionKey(), SUBSCRIBER);
        secrets.add(inquiryPayload);
        String inquiryId =
                JsonPath.read(
                        omar.inquire("svc_elec_cairo", inquiryPayload)
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString(),
                        "$.inquiryId");
        omar.inquire("svc_elec_cairo", inquiryPayload) // replay → DECRYPTION_FAILED path
                .andExpect(status().isBadRequest());

        // confirm: a wrong CVV, a malformed CVV, then the right one, then a byte-identical replay
        String wrongCvv = Payloads.confirm(session.sessionKey(), "321");
        String badCvv = Payloads.confirm(session.sessionKey(), "12a");
        String rightCvv = Payloads.confirm(session.sessionKey(), "123");
        secrets.addAll(List.of(wrongCvv, badCvv, rightCvv));
        omar.confirmWithPayload(inquiryId, UUID.randomUUID(), wrongCvv)
                .andExpect(status().isBadRequest());
        omar.confirmWithPayload(inquiryId, UUID.randomUUID(), badCvv)
                .andExpect(status().isBadRequest());
        UUID key = UUID.randomUUID();
        omar.confirmWithPayload(inquiryId, key, rightCvv).andExpect(status().isOk());
        omar.confirmWithPayload(inquiryId, key, rightCvv).andExpect(status().isOk());

        // malformed bodies: the error path must not echo input either
        mvc.perform(
                        withClientHeaders(post("/v1/payments/confirm"))
                                .header(Headers.USER_ID, "usr_03")
                                .header(Headers.SESSION_ID, session.sessionId())
                                .header(Headers.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"inquiryId\":\""
                                                + inquiryId
                                                + "\",\"payload\":\""
                                                + rightCvv
                                                + "\",\"cvv\":\"123\"}"))
                .andExpect(status().isBadRequest());

        String logs = output.getAll();
        assertThat(logs).contains("******0891"); // masked form is fine and expected
        assertThat(logs).doesNotContain(SUBSCRIBER);
        for (String secret : secrets) {
            assertThat(logs).as("a key or payload leaked into the logs").doesNotContain(secret);
        }
        assertThat(CVV_IN_LOG.matcher(logs).find()).as("a CVV leaked into the logs").isFalse();
        assertThat(logs).doesNotContainIgnoringCase("cvv_hash").doesNotContain("$2a$12$");
    }
}
