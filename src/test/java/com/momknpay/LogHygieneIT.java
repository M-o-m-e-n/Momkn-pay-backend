package com.momknpay;

import static com.momknpay.support.ApiRequests.withClientHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.momknpay.common.web.Headers;
import com.momknpay.support.Payloads;
import com.momknpay.support.PaymentClient;
import com.momknpay.support.TestCrypto;
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
 * I10: runs every sensitive path — inquiry, confirm (right and wrong PIN), replay and malformed
 * input — and asserts that no secret reaches the logs: not the shared payload key, a payload, a PIN
 * or a full subscriber number (NFR-SEC-6, CODING_STANDARDS §10.3).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class LogHygieneIT {

    private static final String SUBSCRIBER = "1024750891";
    private static final Pattern PIN_IN_LOG = Pattern.compile("(?i)\"?pin\"?\\s*[:=]\\s*\"?\\d{4}");

    @Autowired private MockMvc mvc;

    @Test
    void noSecretEverReachesTheLogs(CapturedOutput output) throws Exception {
        List<String> secrets = new ArrayList<>();
        secrets.add(TestCrypto.TEST_PAYLOAD_KEY); // the static key must never be printed
        PaymentClient omar = new PaymentClient(mvc, "usr_03");

        // inquiry: the subscriber number travels encrypted and is logged masked only
        String inquiryPayload = Payloads.inquiry(SUBSCRIBER);
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

        // confirm: a wrong PIN, a malformed PIN, then the right one, then a byte-identical replay
        String wrongPin = Payloads.confirm("1234");
        String badPin = Payloads.confirm("12a4");
        String rightPin = Payloads.confirm("9999");
        secrets.addAll(List.of(wrongPin, badPin, rightPin));
        omar.confirmWithPayload(inquiryId, UUID.randomUUID(), wrongPin)
                .andExpect(status().isBadRequest());
        omar.confirmWithPayload(inquiryId, UUID.randomUUID(), badPin)
                .andExpect(status().isBadRequest());
        UUID key = UUID.randomUUID();
        omar.confirmWithPayload(inquiryId, key, rightPin).andExpect(status().isOk());
        omar.confirmWithPayload(inquiryId, key, rightPin).andExpect(status().isOk());

        // malformed bodies: the error path must not echo input either
        mvc.perform(
                        withClientHeaders(post("/v1/payments/confirm"))
                                .header(Headers.USER_ID, "usr_03")
                                .header(Headers.IDEMPOTENCY_KEY, UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"inquiryId\":\""
                                                + inquiryId
                                                + "\",\"payload\":\""
                                                + rightPin
                                                + "\",\"pin\":\"9999\"}"))
                .andExpect(status().isBadRequest());

        String logs = output.getAll();
        assertThat(logs).contains("******0891"); // masked form is fine and expected
        assertThat(logs).doesNotContain(SUBSCRIBER);
        for (String secret : secrets) {
            assertThat(logs).as("a key or payload leaked into the logs").doesNotContain(secret);
        }
        assertThat(PIN_IN_LOG.matcher(logs).find()).as("a PIN leaked into the logs").isFalse();
        assertThat(logs).doesNotContainIgnoringCase("pin_hash").doesNotContain("$2a$12$");
    }
}
